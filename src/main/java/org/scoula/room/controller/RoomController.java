package org.scoula.room.controller;

import lombok.extern.slf4j.Slf4j;
import org.scoula.room.dto.MessageType;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.scoula.room.dto.RoomResponseDto;
import org.scoula.room.dto.RoomResponseMessage;
import org.scoula.room.service.RoomBroadcaster;
import org.scoula.room.service.RoomCreationRateLimiter;
import org.scoula.room.service.RoomService;
import org.scoula.room.service.WebSocketEventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final RoomBroadcaster roomBroadcaster;
    private final WebSocketEventListener webSocketEventListener;
    private final RoomCreationRateLimiter roomCreationRateLimiter;

    public RoomController(RoomService roomService, RoomBroadcaster roomBroadcaster,
                          WebSocketEventListener webSocketEventListener,
                          RoomCreationRateLimiter roomCreationRateLimiter) {
        this.roomService = roomService;
        this.roomBroadcaster = roomBroadcaster;
        this.webSocketEventListener = webSocketEventListener;
        this.roomCreationRateLimiter = roomCreationRateLimiter;
    }

    @GetMapping("")
    public ResponseEntity<List<RoomResponseDto>> getRooms() {
        List<RoomResponseDto> dtos = roomService.getRoomList().stream()
                .map(RoomResponseDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoomById(@PathVariable String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(RoomResponseDto.from(room));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(
            @RequestParam String title,
            @RequestParam(required = false) String password,
            Authentication authentication) {
        // 신원은 인증 principal(JWT subject)만 사용. principal당 분당 생성 횟수를 제한(방 생성 DoS 차단).
        String principal = authentication.getName();
        if (!roomCreationRateLimiter.tryAcquire(principal)) {
            log.warn("[ROOM_CREATE_RATELIMIT] principal={} title=\"{}\"", principal, title);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many room creations. Try again later.");
        }
        Room room = roomService.createRoom(title, password);
        if (room == null) {
            log.error("[ROOM_CREATE_FAIL] title=\"{}\"", title);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create room");
        }
        log.info("[ROOM_CREATE] title=\"{}\" roomId={} private={}", title, room.getRoomId(), password != null && !password.isBlank());
        return ResponseEntity.ok(room.getRoomId());
    }

    @PostMapping("/join/{roomId}")
    public ResponseEntity<?> joinRoom(
            @PathVariable String roomId,
            @RequestBody Player player,
            @RequestParam(required = false) String password,
            Authentication authentication) {
        // 신원은 인증 principal(JWT subject)만 사용. body player.id/name은 표시용.
        String principal = authentication.getName();
        int result = roomService.joinRoom(roomId, player, password, principal);
        return switch (result) {
            case 1 -> {
                log.info("[JOIN] player=\"{}\"({}) roomId={}", player.name(), player.id(), roomId);
                yield ResponseEntity.ok("Joined successfully");
            }
            case -1 -> {
                log.warn("[JOIN_FAIL] reason=WRONG_PASSWORD player=\"{}\" roomId={}", player.name(), roomId);
                yield ResponseEntity.status(HttpStatus.FORBIDDEN).body("Wrong password");
            }
            default -> {
                log.warn("[JOIN_FAIL] reason=UNAVAILABLE player=\"{}\" roomId={}", player.name(), roomId);
                yield ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Room full or not found");
            }
        };
    }

    @PostMapping("/leave/{roomId}")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomId, Authentication authentication) {
        // 신원은 인증 principal만 사용. 과거 프론트가 보내던 ?playerId= 쿼리는(있어도) 무시한다.
        String principal = authentication.getName();
        Room room = roomService.getRoom(roomId);
        String playerId = room != null ? room.playerIdOf(principal) : null;
        if (playerId == null) {
            log.warn("[LEAVE_FAIL] reason=NOT_MEMBER principal={} roomId={}", principal, roomId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Player not found in the room");
        }

        webSocketEventListener.cancelPendingDisconnect(principal);

        boolean left = roomService.leaveRoom(roomId, playerId);
        if (left) {
            log.info("[LEAVE] playerId={} roomId={}", playerId, roomId);
            roomBroadcaster.broadcast(
                    roomId,
                    RoomResponseMessage.builder().type(MessageType.LEAVE).sender(playerId).build()
            );
            return ResponseEntity.ok("Left the room successfully");
        } else {
            log.warn("[LEAVE_FAIL] playerId={} roomId={}", playerId, roomId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Player not found in the room");
        }
    }


}

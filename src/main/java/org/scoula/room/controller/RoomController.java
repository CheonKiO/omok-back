package org.scoula.room.controller;

import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.Player;
import org.scoula.room.dto.Room;
import org.scoula.room.dto.RoomResponseDto;
import org.scoula.room.dto.RoomResponseMessage;
import org.scoula.room.service.RoomService;
import org.scoula.room.service.WebSocketEventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketEventListener webSocketEventListener;

    public RoomController(RoomService roomService, SimpMessagingTemplate messagingTemplate,
                          WebSocketEventListener webSocketEventListener) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
        this.webSocketEventListener = webSocketEventListener;
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
            @RequestParam(required = false) String password) {
        Room room = roomService.createRoom(title, password);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create room");
        }
        return ResponseEntity.ok(room.getRoomId());
    }

    @PostMapping("/join/{roomId}")
    public ResponseEntity<?> joinRoom(
            @PathVariable String roomId,
            @RequestBody Player player,
            @RequestParam(required = false) String password) {
        int result = roomService.joinRoom(roomId, player, password);
        return switch (result) {
            case  1 -> ResponseEntity.ok("Joined successfully");
            case -1 -> ResponseEntity.status(HttpStatus.FORBIDDEN).body("Wrong password");
            default -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Room full or not found");
        };
    }

    @PostMapping("/leave/{roomId}")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomId, @RequestParam String playerId) {
        // 유예시간 중이었다면 타이머 취소 (명시적 나가기이므로 즉시 처리)
        webSocketEventListener.cancelPendingDisconnect(playerId);

        boolean left = roomService.leaveRoom(roomId, playerId);
        if (left) {
            // 상대방에게 LEAVE 즉시 브로드캐스트
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder().type(MessageType.LEAVE).sender(playerId).build()
            );
            return ResponseEntity.ok("Left the room successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Player not found in the room");
        }
    }


}

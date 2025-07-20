package org.scoula.room.controller;

import org.scoula.room.dto.Player;
import org.scoula.room.dto.Room;
import org.scoula.room.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("")
    public ResponseEntity<List<Room>> getRooms() {
        return ResponseEntity.ok(roomService.getRoomList());
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoomById(@PathVariable String roomId) {
        return ResponseEntity.ok(roomService.getRoom(roomId));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestParam String title) {
        Room room = roomService.createRoom(title);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create room");
        }
        return ResponseEntity.ok(room.getRoomId());
    }

    @PostMapping("/join/{roomId}")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, @RequestBody Player player) {
        boolean joined = roomService.joinRoom(roomId, player);
        if (joined) {
            return ResponseEntity.ok("Joined successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Room full or not found");
        }
    }

    @PostMapping("/leave/{roomId}")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomId, @RequestParam String playerId) {
        boolean left = roomService.leaveRoom(roomId, playerId);
        System.out.println("remain: " + roomService.getRoom(roomId));
        if (left) {
            return ResponseEntity.ok("Left the room successfully");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Player not found in the room");
        }
    }


}

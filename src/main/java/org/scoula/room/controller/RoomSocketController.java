package org.scoula.room.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scoula.room.dto.*;
import org.scoula.room.service.GameService;
import org.scoula.room.service.RoomService;
import org.scoula.room.service.RoomSocketService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;
    private final GameService gameService;
    private final RoomSocketService roomSocketService;

    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@Payload RoomRequestMessage message, StompHeaderAccessor headerAccessor) {
        Player sender = message.sender();
        String roomId = message.roomId();
        log.info("📥 JOIN 요청: {} -> 방 {}", sender, roomId);

        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs != null) {
            attrs.put("roomId", roomId);
            attrs.put("playerId", sender.id());
        }
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage.builder()
                        .sender(sender.id())
                        .roomId(roomId)
                        .type(MessageType.JOIN)
                        .message(sender.name())
                        .build()
        );
    }

    @MessageMapping("/timeout")
    public void timeout(@Payload RoomRequestMessage message) {
        log.info("TIMEOUT 수신 : {}", message);
        if (message.type() != MessageType.TIMEOUT) return;
        String roomId = message.roomId();
        Player sender = message.sender();
        Room room = roomService.getRoom(roomId);
        room.setPlaying(false);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage.builder()
                        .roomId(roomId)
                        .type(MessageType.GAME_END)
                        .message(sender.name() + "님이 시간을 초과하여 게임이 종료되었습니다.")
                        .build()
        );
    }

    @MessageMapping("/ready")
    public void handleReady(@Payload RoomRequestMessage message) {
        log.info("🎮 READY 수신: {}", message);
        String roomId = message.roomId();
        Player sender = message.sender();
        Room room = roomService.getRoom(roomId);
        room.setReady(room.getReady() + 1);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage.builder()
                        .roomId(roomId)
                        .sender(sender.id())
                        .type(MessageType.READY)
                        .message(sender.name())
                        .build()
        );
        if (room.getReady() == 2) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    roomSocketService.notifyGameStart(roomId);
                }
            }, 500);
        }
    }

    @MessageMapping("/surrender")
    public void handleSurrender(@Payload RoomRequestMessage message) {
        log.info("🎮 SURRENDER 수신: {}", message);
        if (message.type() != MessageType.SURRENDER) return;
        String roomId = message.roomId();
        Player sender = message.sender();
        Room room = roomService.getRoom(roomId);
        room.setPlaying(false);
        room.setReady(0);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage.builder()
                        .roomId(roomId)
                        .type(MessageType.GAME_END)
                        .message(sender.name() + "님이 기권하셨습니다.")
                        .build()
        );
    }

    @MessageMapping("/cancel")
    public void handleCancel(@Payload RoomRequestMessage message) {
        log.info("🎮 CANCEL 수신: {}", message);
        String roomId = message.roomId();
        Player sender = message.sender();
        Room room = roomService.getRoom(roomId);
        room.setReady(room.getReady() - 1);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage.builder()
                        .roomId(roomId)
                        .sender(sender.id())
                        .type(MessageType.CANCEL)
                        .message(sender.name())
                        .build()
        );
    }

    @MessageMapping("/move")
    public void handleMove(@Payload RoomRequestMessage message) {
        log.info("🎮 ACTION 수신: {}", message);
        String roomId = message.roomId();
        Player sender = message.sender();
        Integer index = message.index();

        if (message.type() != MessageType.ACTION || index == null) {
            log.warn("❗️부적절한 move 메시지 수신됨: {}", message);
            return;
        }

        Room room = roomService.getRoom(roomId);
        if (room == null || !room.isPlaying()) {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder().roomId(roomId).type(MessageType.ERROR).message("방이 존재하지 않거나, 게임이 진행중이지 않습니다.").build()
            );
            return;
        }

        boolean isBlackTurn = room.getTurn() % 2 == 1;
        boolean senderIsBlack = room.getBlackPlayer().equals(sender.id());
        if (isBlackTurn != senderIsBlack) {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder().roomId(roomId).type(MessageType.ERROR).message("현재 당신의 차례가 아닙니다.").build()
            );
            return;
        }

        if (room.getBoard()[index / 15][index % 15] != 0) {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder().roomId(roomId).type(MessageType.ERROR).message("이미 다른 돌이 존재합니다.").build()
            );
            return;
        }

        if (room.getTurn() % 2 == 1 && gameService.isForbiddenMove(room, index)) {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder().roomId(roomId).type(MessageType.ERROR).message("금수 위치입니다.").build()
            );
            return;
        }

        gameService.applyMove(room, index);
        int turn = room.getTurn();

        if (gameService.checkGameEnd(room, index)) {
            room.setPlaying(false);
            room.setReady(0);
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder()
                            .roomId(roomId)
                            .type(MessageType.GAME_END)
                            .message(sender.name() + "님이 승리하셨습니다")
                            .index(index)
                            .turn(turn)
                            .build()
            );
        } else {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder()
                            .roomId(roomId)
                            .type(MessageType.ACTION)
                            .index(index)
                            .turn(turn)
                            .build()
            );
        }
    }
}

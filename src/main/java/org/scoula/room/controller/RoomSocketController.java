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


    // 클라이언트가 /app/room/{roomId}/join 으로 메시지 보내면 실행됨
    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@Payload RoomRequestMessage message, StompHeaderAccessor headerAccessor) {
        Player sender = message.getSender();
        log.info("📥 JOIN 요청: {} -> 방 {}", sender, message.getRoomId());
        String roomId = message.getRoomId();

        // 세션에 roomId, playerId 저장
        headerAccessor.getSessionAttributes().put("roomId", roomId);
        headerAccessor.getSessionAttributes().put("playerId", sender.getId());
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage
                        .builder()
                        .sender(sender.getId())
                        .roomId(roomId)
                        .type(MessageType.JOIN)
                        .message(sender.getName())
                        .build()
        );
    }

    @MessageMapping("/timeout")
    public void timeout(@Payload RoomRequestMessage message) {
        log.info("TIMEOUT 수신 : {}", message);
        // 비정상 요청
        if(!message.getType().equals(MessageType.TIMEOUT)) return;
        String roomId = message.getRoomId();
        Player sender = message.getSender();
        Room room = roomService.getRoom(roomId);
        room.setPlaying(false);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage
                        .builder()
                        .roomId(roomId)
                        .type(MessageType.GAME_END)
                        .message(sender.getName() + "님이 시간을 초과하여 게임이 종료되었습니다.")
                        .build()
        );
    }

    @MessageMapping("/ready")
    public void handleReady(@Payload RoomRequestMessage roomRequestMessage) {
        log.info("🎮 READY 수신: {}", roomRequestMessage);
        String roomId = roomRequestMessage.getRoomId();
        Player sender = roomRequestMessage.getSender();
        Room room = roomService.getRoom(roomId);
        System.out.println("before ready: " + room.getReady());
        room.setReady(room.getReady()+1);
        System.out.println("after ready: " + room.getReady());
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage.builder().roomId(roomId).type(MessageType.READY).message(sender.getName()).build()
        );
        if(room.getReady() == 2){
            //방 입장 후 메시지를 전송하기 위한 지연
            System.out.println("game start");
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    roomSocketService.notifyGameStart(roomId);
                }
            }, 500); // 0.5초 지연
        }
    }

    @MessageMapping("/surrender")
    public void handleSurrender(@Payload RoomRequestMessage roomRequestMessage) {
        log.info("🎮 SURRENDER 수신: {}", roomRequestMessage);
        if(!roomRequestMessage.getType().equals(MessageType.SURRENDER)) return;
        String roomId = roomRequestMessage.getRoomId();
        Player sender = roomRequestMessage.getSender();

        Room room = roomService.getRoom(roomId);

        room.setPlaying(false);
        room.setReady(0);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage
                        .builder()
                        .roomId(roomId)
                        .type(MessageType.GAME_END)
                        .message(sender.getName() + "님이 기권하셨습니다.")
                        .build()
        );
    }
    @MessageMapping("/cancel")
    public void handleCancel(@Payload RoomRequestMessage roomRequestMessage) {
        log.info("🎮 CANCEL 수신: {}", roomRequestMessage);
        String roomId = roomRequestMessage.getRoomId();
        Player sender = roomRequestMessage.getSender();
        Room room = roomService.getRoom(roomId);
        room.setReady(room.getReady()-1);
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                RoomResponseMessage.builder().roomId(roomId).type(MessageType.CANCEL).message(sender.getName()).build()
        );
    }
    // 게임 중 돌 놓기 등의 행동 처리
    @MessageMapping("/move")
    public void handleMove(@Payload RoomRequestMessage roomRequestMessage) {
        log.info("🎮 ACTION 수신: {}", roomRequestMessage);

        String roomId = roomRequestMessage.getRoomId();
        Player sender = roomRequestMessage.getSender();
        Integer index = roomRequestMessage.getIndex();
        if (roomRequestMessage.getType() != MessageType.ACTION || index == null) {
            log.warn("❗️부적절한 move 메시지 수신됨: {}", roomRequestMessage);
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


        // 중복 요청 차단
        if(room.getBoard()[index/15][index%15] != 0){
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder().roomId(roomId).type(MessageType.ERROR).message("이미 다른 돌이 존재합니다.").build()
            );
            return;
        }


        // 🔒 금수 검사 - 흑돌만 검사
        if (index % 2 == 1 && gameService.isForbiddenMove(room, index)) {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder().roomId(roomId).type(MessageType.ERROR).message("금수 위치입니다.").build()
            );
            return;
        }

        // ✔️ 정상 착수
        gameService.applyMove(room, index);
        int turn  = room.getTurn();
        if (gameService.checkGameEnd(room, index)) {
            // 🎯 게임 종료
            room.setPlaying(false);
            room.setReady(0);
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage
                            .builder()
                            .roomId(roomId)
                            .type(MessageType.GAME_END)
                            .message(sender.getName() + "님이 승리하셨습니다")
                            .index(index)
                            .turn(turn)
                            .build()
            );
        } else {
            // 🔁 일반 턴 진행
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage
                            .builder()
                            .roomId(roomId)
                            .type(MessageType.ACTION)
                            .index(index)
                            .turn(turn)
                            .build()
            );
        }
    }

}

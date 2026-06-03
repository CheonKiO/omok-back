package org.scoula.room.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.Player;
import org.scoula.room.dto.RoomRequestMessage;
import org.scoula.room.dto.RoomResponseMessage;
import org.scoula.room.service.RoomSocketService;
import org.scoula.room.service.WebSocketEventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomSocketService roomSocketService;
    private final WebSocketEventListener webSocketEventListener;

    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@Payload RoomRequestMessage message, StompHeaderAccessor headerAccessor) {
        Player sender = message.sender();
        String roomId = message.roomId();

        boolean isReconnect = webSocketEventListener.cancelPendingDisconnect(sender.id());
        log.info(isReconnect ? "🔄 재연결: {}" : "📥 신규 입장: {}", sender.name());

        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs != null) {
            attrs.put("roomId", roomId);
            attrs.put("playerId", sender.id());
        }

        MessageType type = isReconnect ? MessageType.RECONNECT : MessageType.JOIN;
        messagingTemplate.convertAndSend("/topic/room/" + roomId,
                RoomResponseMessage.builder()
                        .sender(sender.id())
                        .roomId(roomId)
                        .type(type)
                        .message(sender.name())
                        .build());
    }

    @MessageMapping("/ready")
    public void handleReady(@Payload RoomRequestMessage message) {
        log.info("🎮 READY 수신: {}", message);
        roomSocketService.processReady(message.roomId(), message.sender());
    }

    @MessageMapping("/cancel")
    public void handleCancel(@Payload RoomRequestMessage message) {
        log.info("🎮 CANCEL 수신: {}", message);
        roomSocketService.processCancel(message.roomId(), message.sender());
    }

    @MessageMapping("/surrender")
    public void handleSurrender(@Payload RoomRequestMessage message) {
        if (message.type() != MessageType.SURRENDER) return;
        log.info("🎮 SURRENDER 수신: {}", message);
        roomSocketService.processSurrender(message.roomId(), message.sender());
    }

    @MessageMapping("/timeout")
    public void timeout(@Payload RoomRequestMessage message) {
        if (message.type() != MessageType.TIMEOUT) return;
        log.info("TIMEOUT 수신: {}", message);
        roomSocketService.processTimeout(message.roomId(), message.sender());
    }

    @MessageMapping("/move")
    public void handleMove(@Payload RoomRequestMessage message) {
        if (message.type() != MessageType.ACTION || message.index() == null) {
            log.warn("❗️부적절한 move 메시지: {}", message);
            return;
        }
        log.info("🎮 ACTION 수신: roomId={}, index={}", message.roomId(), message.index());
        roomSocketService.processMove(message.roomId(), message.sender(), message.index());
    }
}

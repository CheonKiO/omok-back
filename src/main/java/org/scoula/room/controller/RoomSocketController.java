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
        log.info("[WS_JOIN] player=\"{}\"({}) roomId={} reconnect={}", sender.name(), sender.id(), roomId, isReconnect);

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
        log.info("[READY] player=\"{}\" roomId={}", message.sender().name(), message.roomId());
        roomSocketService.processReady(message.roomId(), message.sender());
    }

    @MessageMapping("/cancel")
    public void handleCancel(@Payload RoomRequestMessage message) {
        log.info("[CANCEL] player=\"{}\" roomId={}", message.sender().name(), message.roomId());
        roomSocketService.processCancel(message.roomId(), message.sender());
    }

    @MessageMapping("/surrender")
    public void handleSurrender(@Payload RoomRequestMessage message) {
        if (message.type() != MessageType.SURRENDER) return;
        roomSocketService.processSurrender(message.roomId(), message.sender());
    }

    @MessageMapping("/timeout")
    public void timeout(@Payload RoomRequestMessage message) {
        if (message.type() != MessageType.TIMEOUT) return;
        roomSocketService.processTimeout(message.roomId(), message.sender());
    }

    @MessageMapping("/move")
    public void handleMove(@Payload RoomRequestMessage message) {
        if (message.type() != MessageType.ACTION || message.index() == null) {
            log.warn("[MOVE_INVALID] player=\"{}\" roomId={}", message.sender().name(), message.roomId());
            return;
        }
        roomSocketService.processMove(message.roomId(), message.sender(), message.index());
    }
}

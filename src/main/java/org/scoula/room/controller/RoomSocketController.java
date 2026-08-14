package org.scoula.room.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.Player;
import org.scoula.room.dto.RoomRequestMessage;
import org.scoula.room.dto.RoomResponseMessage;
import org.scoula.room.service.RoomBroadcaster;
import org.scoula.room.service.RoomSocketService;
import org.scoula.room.service.WebSocketEventListener;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomSocketController {

    private final RoomBroadcaster roomBroadcaster;
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
        roomBroadcaster.broadcast(roomId,
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

    /** STOMP 메시지 처리 중 발생한 예외를 발신 클라이언트에게 에러 프레임으로 전달. */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public RoomResponseMessage handleException(Exception e) {
        log.error("[WS_ERROR] {}", e.getMessage(), e);
        return RoomResponseMessage.builder()
                .type(MessageType.ERROR)
                .message("요청 처리 중 오류가 발생했습니다.")
                .build();
    }
}

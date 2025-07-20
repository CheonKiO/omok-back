package org.scoula.room.service;

import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.RoomResponseMessage;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Objects;

@Component
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate, RoomService roomService) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        // 필요하다면, 세션 ID로 유저 정보 찾아오기
        String sessionId = headerAccessor.getSessionId();

        // 퇴장 메시지 브로드캐스트
        String roomId = (String) Objects.requireNonNull(headerAccessor.getSessionAttributes()).get("roomId");
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");

        System.out.println("Disconnected from " + sessionId + " room " + roomId + " " + playerId);
        if (roomId != null && playerId != null) {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder().type(MessageType.LEAVE).sender(playerId).build()
            );
        }

        System.out.println("🛑 웹소켓 연결 종료: 세션 " + sessionId);
    }
}

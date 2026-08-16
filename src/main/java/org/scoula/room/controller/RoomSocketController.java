package org.scoula.room.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scoula.room.dto.MessageType;
import org.scoula.room.domain.Player;
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

import java.security.Principal;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomSocketController {

    private final RoomBroadcaster roomBroadcaster;
    private final RoomSocketService roomSocketService;
    private final WebSocketEventListener webSocketEventListener;

    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@Payload RoomRequestMessage message, StompHeaderAccessor headerAccessor, Principal principal) {
        Player sender = message.sender();
        String roomId = message.roomId();

        // 재접속 유예 취소는 위조 불가한 principal 앵커로만. payload id는 인가 신원 아님.
        String principalName = principal != null ? principal.getName() : null;
        boolean isReconnect = webSocketEventListener.cancelPendingDisconnect(principalName);
        log.info("[WS_JOIN] player=\"{}\"({}) principal={} roomId={} reconnect={}", sender.name(), sender.id(), principalName, roomId, isReconnect);

        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs != null) {
            // 이전 JOIN이 남긴 방 키에서 이 세션을 먼저 떼어낸다. 소켓 종료 시 해제되는 키는
            // 마지막 attrs.roomId 하나뿐이라, 이 정리가 없으면 이전 방 키에 죽은 세션이 남아
            // 그 방에서의 진짜 끊김이 "다른 탭 생존"으로 오인된다(레지스트리도 무한 증가).
            String previousRoomId = (String) attrs.get("roomId");
            if (previousRoomId != null && !previousRoomId.equals(roomId)) {
                webSocketEventListener.releaseSession(principalName, previousRoomId, headerAccessor.getSessionId());
            }
            attrs.put("roomId", roomId);
            attrs.put("playerId", sender.id());
            if (principalName != null) attrs.put("principal", principalName);
        }

        // 중복 탭 안전망: (principal, roomId)별 활성 세션을 등록해 두면
        // 한 탭만 닫혔을 때 유예/몰수가 잘못 발동하지 않는다.
        webSocketEventListener.registerSession(principalName, roomId, headerAccessor.getSessionId());

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
    public void handleReady(@Payload RoomRequestMessage message, Principal principal) {
        log.info("[READY] player=\"{}\" roomId={}", message.sender().name(), message.roomId());
        roomSocketService.processReady(message.roomId(), nameOf(principal));
    }

    @MessageMapping("/cancel")
    public void handleCancel(@Payload RoomRequestMessage message, Principal principal) {
        log.info("[CANCEL] player=\"{}\" roomId={}", message.sender().name(), message.roomId());
        roomSocketService.processCancel(message.roomId(), nameOf(principal));
    }

    @MessageMapping("/surrender")
    public void handleSurrender(@Payload RoomRequestMessage message, Principal principal) {
        if (message.type() != MessageType.SURRENDER) return;
        roomSocketService.processSurrender(message.roomId(), nameOf(principal));
    }

    @MessageMapping("/timeout")
    public void timeout(@Payload RoomRequestMessage message, Principal principal) {
        if (message.type() != MessageType.TIMEOUT) return;
        roomSocketService.processTimeout(message.roomId(), nameOf(principal));
    }

    @MessageMapping("/move")
    public void handleMove(@Payload RoomRequestMessage message, Principal principal) {
        if (message.type() != MessageType.ACTION || message.index() == null) {
            log.warn("[MOVE_INVALID] player=\"{}\" roomId={}", message.sender().name(), message.roomId());
            return;
        }
        roomSocketService.processMove(message.roomId(), nameOf(principal), message.index());
    }

    /** 세션 principal 이름(JWT subject). 미인증(익명) CONNECT면 null → 서비스가 인가 거부. */
    private static String nameOf(Principal principal) {
        return principal == null ? null : principal.getName();
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

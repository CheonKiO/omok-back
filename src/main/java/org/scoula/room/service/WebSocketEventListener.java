package org.scoula.room.service;

import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.Room;
import org.scoula.room.dto.RoomResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WebSocketEventListener {

    private static final int GRACE_PERIOD_SECONDS = 30;

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate, RoomService roomService) {
        this.messagingTemplate = messagingTemplate;
        this.roomService = roomService;
    }

    // RoomSocketController에서 JOIN 수신 시 호출 - 재연결이면 true 반환
    public boolean cancelPendingDisconnect(String playerId) {
        ScheduledFuture<?> future = pendingDisconnects.remove(playerId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            return true;
        }
        return false;
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) return;
        String roomId = (String) attrs.get("roomId");
        String playerId = (String) attrs.get("playerId");

        if (roomId == null || playerId == null) return;

        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        // 이미 HTTP leave API로 정상 퇴장한 경우 무시
        boolean isStillInRoom = room.getPlayers().stream().anyMatch(p -> p.id().equals(playerId));
        if (!isStillInRoom) {
            log.debug("[WS_CLOSE] 정상 퇴장 후 소켓 종료 (무시) sessionId={}", sessionId);
            return;
        }

        if (room.isPlaying()) {
            // 게임 중 연결 끊김 → 유예 시간 부여
            log.warn("[WS_DISCONNECT] playerId={} roomId={} grace={}s", playerId, roomId, GRACE_PERIOD_SECONDS);
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder()
                            .type(MessageType.DISCONNECTED)
                            .sender(playerId)
                            .build()
            );

            ScheduledFuture<?> future = scheduler.schedule(() -> {
                pendingDisconnects.remove(playerId);
                roomService.leaveRoom(roomId, playerId);
                messagingTemplate.convertAndSend(
                        "/topic/room/" + roomId,
                        RoomResponseMessage.builder()
                                .type(MessageType.LEAVE)
                                .sender(playerId)
                                .build()
                );
                log.warn("[GRACE_EXPIRE] playerId={} roomId={}", playerId, roomId);
            }, GRACE_PERIOD_SECONDS, TimeUnit.SECONDS);

            pendingDisconnects.put(playerId, future);
        } else {
            // 게임 중이 아닐 때 → 즉시 퇴장
            roomService.leaveRoom(roomId, playerId);
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId,
                    RoomResponseMessage.builder()
                            .type(MessageType.LEAVE)
                            .sender(playerId)
                            .build()
            );
            log.info("[WS_DISCONNECT] playerId={} roomId={} reason=NOT_PLAYING", playerId, roomId);
        }
    }
}

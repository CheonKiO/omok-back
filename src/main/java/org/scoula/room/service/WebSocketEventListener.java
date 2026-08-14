package org.scoula.room.service;

import org.scoula.room.dto.MessageType;
import org.scoula.room.domain.Room;
import org.scoula.room.dto.RoomResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
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

    private final RoomBroadcaster roomBroadcaster;
    private final RoomService roomService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();

    public WebSocketEventListener(RoomBroadcaster roomBroadcaster, RoomService roomService) {
        this.roomBroadcaster = roomBroadcaster;
        this.roomService = roomService;
    }

    // RoomSocketController/HTTP leave에서 호출 - 유예 창의 앵커는 위조 불가한 principal.
    // 같은 principal만 자신의 유예를 취소(재접속)할 수 있다. 재연결이면 true 반환.
    public boolean cancelPendingDisconnect(String principal) {
        if (principal == null) return false;
        ScheduledFuture<?> future = pendingDisconnects.remove(principal);
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
        if (roomId == null) return;

        // 신원 앵커는 CONNECT에서 바인딩된 principal(위조 불가). 이벤트 우선, 없으면 세션 attrs fallback.
        String principal = event.getUser() != null ? event.getUser().getName() : (String) attrs.get("principal");

        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        // 방출 대상 playerId는 payload에서 온 attrs.playerId가 아니라 principal→자리 바인딩에서 도출한다.
        // (attrs.playerId는 WS join의 무검증 sender.id라, 그대로 쓰면 남의 자리를 방출하는 자리탈취 벡터.)
        String playerId = principal != null ? room.playerIdOf(principal) : null;
        if (playerId == null) return; // 비멤버/익명 → 정리할 자리 없음

        // 이미 HTTP leave API로 정상 퇴장한 경우 무시
        boolean isStillInRoom = room.getPlayers().stream().anyMatch(p -> p.id().equals(playerId));
        if (!isStillInRoom) {
            log.debug("[WS_CLOSE] 정상 퇴장 후 소켓 종료 (무시) sessionId={}", sessionId);
            return;
        }

        if (room.isPlaying() && principal != null) {
            // 게임 중 연결 끊김 → 유예 시간 부여. 유예 앵커는 위조 불가한 principal 키.
            log.warn("[WS_DISCONNECT] playerId={} principal={} roomId={} grace={}s", playerId, principal, roomId, GRACE_PERIOD_SECONDS);
            roomBroadcaster.broadcast(
                    roomId,
                    RoomResponseMessage.builder()
                            .type(MessageType.DISCONNECTED)
                            .sender(playerId)
                            .build()
            );

            final String gracePrincipal = principal;
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                pendingDisconnects.remove(gracePrincipal);
                roomService.leaveRoom(roomId, playerId);
                roomBroadcaster.broadcast(
                        roomId,
                        RoomResponseMessage.builder()
                                .type(MessageType.LEAVE)
                                .sender(playerId)
                                .build()
                );
                log.warn("[GRACE_EXPIRE] playerId={} principal={} roomId={}", playerId, gracePrincipal, roomId);
            }, GRACE_PERIOD_SECONDS, TimeUnit.SECONDS);

            pendingDisconnects.put(principal, future);
        } else {
            // 게임 중이 아닐 때 → 즉시 퇴장
            roomService.leaveRoom(roomId, playerId);
            roomBroadcaster.broadcast(
                    roomId,
                    RoomResponseMessage.builder()
                            .type(MessageType.LEAVE)
                            .sender(playerId)
                            .build()
            );
            log.info("[WS_DISCONNECT] playerId={} roomId={} reason=NOT_PLAYING", playerId, roomId);
        }
    }
}

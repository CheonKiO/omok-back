package org.scoula.room.service;

import org.scoula.room.dto.MessageType;
import org.scoula.room.domain.Room;
import org.scoula.room.dto.RoomResponseMessage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
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
    private final org.scoula.game.GameArchiveService gameArchiveService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();
    // (principal, roomId)별 활성 WS 세션 집합. 같은 사용자가 여러 탭을 열었을 때
    // 마지막 세션이 끊길 때만 유예/퇴장을 처리하기 위한 근거(D5).
    private final ConcurrentHashMap<String, Set<String>> sessionsByMember = new ConcurrentHashMap<>();

    public WebSocketEventListener(RoomBroadcaster roomBroadcaster, RoomService roomService,
                                  org.scoula.game.GameArchiveService gameArchiveService) {
        this.roomBroadcaster = roomBroadcaster;
        this.roomService = roomService;
        this.gameArchiveService = gameArchiveService;
    }

    // 빈 소멸 시 유예 스케줄러를 정리해 스레드/작업 누수를 막는다.
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
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

    private static String memberKey(String principal, String roomId) {
        return principal + "|" + roomId;
    }

    /** WS JOIN 시 세션을 등록한다. 인증 principal이 없으면 추적하지 않는다. */
    public void registerSession(String principal, String roomId, String sessionId) {
        if (principal == null || roomId == null || sessionId == null) return;
        // compute로 등록/해제를 같은 키 락 아래 직렬화한다. computeIfAbsent 후 별도로 add하면
        // 해제 쪽이 그 사이 빈 집합을 맵에서 제거해 방금 등록한 세션을 잃을 수 있다.
        sessionsByMember.compute(memberKey(principal, roomId), (k, sessions) -> {
            Set<String> target = (sessions == null) ? ConcurrentHashMap.newKeySet() : sessions;
            target.add(sessionId);
            return target;
        });
    }

    /**
     * 한 세션이 다른 방으로 JOIN할 때 이전 방 키에서 그 세션을 떼어낸다.
     * 소켓 종료 시 해제되는 키는 마지막 attrs.roomId 하나뿐이라, 이 정리가 없으면
     * 이전 방 키에 죽은 sessionId가 영구히 남아(레지스트리도 무한 증가) 그 방에서의
     * 진짜 끊김이 "다른 탭 생존"으로 오인돼 유예/몰수가 영영 발동하지 않는다.
     */
    public void releaseSession(String principal, String roomId, String sessionId) {
        if (sessionId == null) return; // registerSession도 null은 추적하지 않는다
        unregisterSession(principal, roomId, sessionId);
    }

    /** 세션을 해제하고, 그 사용자의 마지막 세션이었으면 true를 반환한다. */
    private boolean unregisterSession(String principal, String roomId, String sessionId) {
        if (principal == null || roomId == null) return true;
        // 남은 세션이 없으면 키까지 제거(맵 누수 방지)하고 null을 돌려받는다.
        // 애초에 추적하지 못한 세션도 null → true, 즉 기존 유예/퇴장 동작을 유지한다.
        Set<String> remaining = sessionsByMember.computeIfPresent(memberKey(principal, roomId), (k, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
        return remaining == null;
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

        // 같은 사용자의 다른 탭이 아직 살아 있으면 이 세션 종료는 무시한다.
        // (무시하지 않으면 접속 중인 사용자가 30초 뒤 몰수패한다.)
        // 아래 방/자리 검사보다 앞에 둔다: 정상 퇴장·방 소멸 경로에서 early return에 걸리면
        // 세션이 해제되지 않아, 같은 방에 재입장한 뒤의 진짜 끊김이 통째로 무시된다.
        if (!unregisterSession(principal, roomId, sessionId)) {
            log.debug("[WS_CLOSE] 다른 탭 세션 생존 → 유예 생략 principal={} roomId={}", principal, roomId);
            return;
        }

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
                // 끊김 몰수: 끊긴 principal이 패 → 상대 승. leaveRoom(자리 principal unbind) 전에 기보 저장.
                // 다른 종료 경로(processMove/surrender/timeout)와 가시성·중복저장 일관성 위해 room 락 안에서.
                Room graceRoom = roomService.getRoom(roomId);
                org.scoula.game.WinnerColor graceWinner = null;
                if (graceRoom != null) {
                    synchronized (graceRoom) {
                        if (graceRoom.isPlaying()) {
                            graceWinner = gracePrincipal.equals(graceRoom.blackPrincipal())
                                    ? org.scoula.game.WinnerColor.WHITE : org.scoula.game.WinnerColor.BLACK;
                            graceRoom.setPlaying(false); // 종료 표시 → 타 경로 이중저장 차단
                            gameArchiveService.archive(graceRoom, graceWinner, org.scoula.game.EndReason.DISCONNECT);
                        }
                    }
                }
                if (graceWinner != null) {
                    roomBroadcaster.broadcast(
                            roomId,
                            RoomResponseMessage.builder()
                                    .type(MessageType.GAME_END)
                                    .message("상대가 연결을 회복하지 못해 게임이 종료되었습니다.")
                                    .winner(graceWinner.name())
                                    .build()
                    );
                }
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

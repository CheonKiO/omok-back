package org.scoula.room.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 재접속(reconnect) 유예를 위조 불가한 principal 앵커로 검증 (#1).
 * 유예 창은 CONNECT에서 바인딩된 principal(event.getUser())로 키가 잡히므로,
 * 같은 principal만 유예를 취소(재접속)할 수 있고, 다른 principal이나
 * 클라 제공 playerId로는 남의 유예를 취소해 자리를 가로챌 수 없다.
 */
class ReconnectTest {

    private RoomBroadcaster roomBroadcaster;
    private RoomService roomService;
    private WebSocketEventListener listener;

    private static final String ROOM_ID = "room-1";
    private static final String PRINCIPAL_A = "user:A";
    private static final String PLAYER_ID_A = "p-A";

    @BeforeEach
    void setUp() {
        roomBroadcaster = mock(RoomBroadcaster.class);
        roomService = mock(RoomService.class);
        listener = new WebSocketEventListener(roomBroadcaster, roomService,
                mock(org.scoula.game.GameArchiveService.class));
    }

    /** 진행중(isPlaying)인 방에 principal=user:A(자리 playerId=p-A)가 아직 남아있는 상태.
     *  실제 흐름과 동일하게 HTTP join이 하듯 principal→playerId를 bindMember로 묶어둔다
     *  (disconnect 핸들러가 방출 대상을 room.playerIdOf(principal)로 도출하므로 필수). */
    private Room playingRoomWith(String playerId) {
        Room room = Room.builder()
                .roomId(ROOM_ID)
                .players(new ArrayList<>(List.of(new Player(playerId, "흑돌"))))
                .board(new int[15][15])
                .turn(1)
                .isPlaying(true)
                .build();
        room.bindMember(PRINCIPAL_A, playerId, "흑돌");
        return room;
    }

    /** CONNECT에서 바인딩된 principal을 담은 DISCONNECT 이벤트를 만든다(세션 attrs 포함). */
    private SessionDisconnectEvent disconnectEvent(String sessionId, String principalName, String playerId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("roomId", ROOM_ID);
        attrs.put("playerId", playerId);
        attrs.put("principal", principalName);
        accessor.setSessionAttributes(attrs);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Principal user = new UsernamePasswordAuthenticationToken(principalName, null);
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL, user);
    }

    @Test
    void samePrincipalReconnectCancelsGrace() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(playingRoomWith(PLAYER_ID_A));

        // A가 게임 중 연결이 끊겨 유예 등록됨
        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-A", PRINCIPAL_A, PLAYER_ID_A));

        // 같은 principal A로 재접속 → 유예 취소 성공(true)
        assertTrue(listener.cancelPendingDisconnect(PRINCIPAL_A),
                "같은 principal 재접속은 유예를 취소해야 한다");
    }

    @Test
    void clientPlayerIdCannotCancelGrace() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(playingRoomWith(PLAYER_ID_A));

        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-A", PRINCIPAL_A, PLAYER_ID_A));

        // 유예 키는 principal이지 클라 제공 playerId가 아니다 → playerId로는 취소 불가
        assertFalse(listener.cancelPendingDisconnect(PLAYER_ID_A),
                "클라 제공 playerId로는 남의 유예를 취소할 수 없어야 한다");
        // A의 유예는 그대로 유지됨(뒤늦은 A 취소가 성공)
        assertTrue(listener.cancelPendingDisconnect(PRINCIPAL_A),
                "다른 키의 취소 시도 후에도 A의 유예는 유지되어야 한다");
    }

    @Test
    void forgedPlayerIdInAttrsCannotEvictVictimOnDisconnect() {
        // 방엔 피해자(principal=user:A, 자리 p-A)만 바인딩됨.
        when(roomService.getRoom(ROOM_ID)).thenReturn(playingRoomWith(PLAYER_ID_A));

        // 공격자 세션: event principal=user:attacker(비멤버), 그러나 attrs.playerId=피해자 p-A로 위조.
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("sess-attacker");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("roomId", ROOM_ID);
        attrs.put("playerId", PLAYER_ID_A); // 위조된 피해자 id
        attrs.put("principal", "user:attacker");
        accessor.setSessionAttributes(attrs);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Principal attacker = new UsernamePasswordAuthenticationToken("user:attacker", null);
        SessionDisconnectEvent evt = new SessionDisconnectEvent(this, message, "sess-attacker", CloseStatus.NORMAL, attacker);

        listener.handleWebSocketDisconnectListener(evt);

        // 방출 대상은 room.playerIdOf(공격자 principal)=null → 조기 반환. 피해자 유예 등록 안 됨,
        // leaveRoom 호출도 없음(피해자 강퇴 불가).
        org.mockito.Mockito.verify(roomService, org.mockito.Mockito.never()).leaveRoom(ROOM_ID, PLAYER_ID_A);
        assertFalse(listener.cancelPendingDisconnect("user:attacker"),
                "비멤버 공격자의 disconnect는 유예를 만들지 않는다");
    }

    @Test
    void otherPrincipalCannotCancelAnothersGrace() {
        when(roomService.getRoom(ROOM_ID)).thenReturn(playingRoomWith(PLAYER_ID_A));

        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-A", PRINCIPAL_A, PLAYER_ID_A));

        // 다른 principal B가 취소 시도 → 실패(false), A 유예 유지
        assertFalse(listener.cancelPendingDisconnect("user:B"),
                "다른 principal은 남의 유예를 취소할 수 없어야 한다");
        assertTrue(listener.cancelPendingDisconnect(PRINCIPAL_A),
                "다른 principal의 취소 시도 후에도 A의 유예는 유지되어야 한다");
    }
}

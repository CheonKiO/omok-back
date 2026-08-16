package org.scoula.room.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.scoula.room.dto.MessageType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 중복 탭(front #2, D5): 같은 principal이 한 방에 두 세션을 열고 한 세션만 끊겨도
 * 아직 살아있는 세션이 있으면 DISCONNECTED 유예를 시작하지 않는다.
 * 시작해 버리면 멀쩡히 접속 중인 사용자가 30초 뒤 몰수패한다.
 */
class MultiSessionDisconnectTest {

    private RoomBroadcaster roomBroadcaster;
    private RoomService roomService;
    private WebSocketEventListener listener;

    private static final String ROOM_ID = "room-1";
    private static final String PRINCIPAL = "user:2";

    @BeforeEach
    void setUp() {
        roomBroadcaster = mock(RoomBroadcaster.class);
        roomService = mock(RoomService.class);
        listener = new WebSocketEventListener(roomBroadcaster, roomService,
                mock(org.scoula.game.GameArchiveService.class));

        Room room = Room.builder()
                .roomId(ROOM_ID)
                .players(new ArrayList<>(List.of(new Player("p1", "나"), new Player("p2", "상대"))))
                .board(new int[15][15])
                .turn(1)
                .isPlaying(true)
                .build();
        room.bindMember(PRINCIPAL, "p1", "나");
        room.bindMember("user:3", "p2", "상대");
        room.setBlackPrincipal(PRINCIPAL);
        room.setWhitePrincipal("user:3");
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("roomId", ROOM_ID);
        attrs.put("principal", PRINCIPAL);
        accessor.setSessionAttributes(attrs);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Principal user = () -> PRINCIPAL;
        return new SessionDisconnectEvent(this, message, sessionId,
                org.springframework.web.socket.CloseStatus.NORMAL, user);
    }

    @Test
    void 두_탭_중_하나만_끊기면_유예를_시작하지_않는다() {
        listener.registerSession(PRINCIPAL, ROOM_ID, "sess-A");
        listener.registerSession(PRINCIPAL, ROOM_ID, "sess-B");

        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-A"));

        verify(roomBroadcaster, never()).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.DISCONNECTED));
    }

    @Test
    void 마지막_세션이_끊기면_유예를_시작한다() {
        listener.registerSession(PRINCIPAL, ROOM_ID, "sess-A");
        listener.registerSession(PRINCIPAL, ROOM_ID, "sess-B");

        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-A"));
        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-B"));

        verify(roomBroadcaster, times(1)).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.DISCONNECTED));
    }
}

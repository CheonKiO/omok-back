package org.scoula.room.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoula.room.controller.RoomSocketController;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.RoomRequestMessage;
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
    private Room room;

    private static final String ROOM_ID = "room-1";
    private static final String PRINCIPAL = "user:2";

    @BeforeEach
    void setUp() {
        roomBroadcaster = mock(RoomBroadcaster.class);
        roomService = mock(RoomService.class);
        listener = new WebSocketEventListener(roomBroadcaster, roomService,
                mock(org.scoula.game.GameArchiveService.class));

        room = Room.builder()
                .roomId(ROOM_ID)
                .players(new ArrayList<>(List.of(new Player("p1", "나"), new Player("p2", "상대"))))
                .board(new int[15][15])
                .turn(1)
                .isPlaying(true)
                .build();
        room.bindMember(PRINCIPAL, "p1");
        room.bindMember("user:3", "p2");
        room.setBlackPrincipal(PRINCIPAL);
        room.setWhitePrincipal("user:3");
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        return disconnectEvent(sessionId, ROOM_ID);
    }

    /** attrs.roomId가 임의의 방을 가리키는 소켓 종료 이벤트(마지막 JOIN이 남긴 값). */
    private SessionDisconnectEvent disconnectEvent(String sessionId, String attrRoomId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("roomId", attrRoomId);
        attrs.put("principal", PRINCIPAL);
        accessor.setSessionAttributes(attrs);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Principal user = () -> PRINCIPAL;
        return new SessionDisconnectEvent(this, message, sessionId,
                org.springframework.web.socket.CloseStatus.NORMAL, user);
    }

    /** 한 소켓 세션의 STOMP 헤더. attrs 맵은 실제 세션처럼 JOIN 간에 공유된다. */
    private StompHeaderAccessor joinAccessor(String sessionId, Map<String, Object> attrs) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(sessionId);
        accessor.setSessionAttributes(attrs);
        return accessor;
    }

    private RoomRequestMessage joinMessage(String roomId) {
        return new RoomRequestMessage(new Player("p1", "나"), roomId, MessageType.JOIN, null);
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

    @Test
    void 정상_퇴장_후_재입장한_사용자도_끊기면_유예를_시작한다() {
        listener.registerSession(PRINCIPAL, ROOM_ID, "sess-A");

        // HTTP leave(RoomServiceImpl.leaveRoom)로 정상 퇴장 → 자리 언바인딩. 뒤이어 소켓이 닫힌다.
        room.getPlayers().removeIf(p -> p.id().equals("p1"));
        room.unbindMember(PRINCIPAL);
        room.setPlaying(false);
        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-A"));

        // 같은 방에 다시 입장해 대국 재개. 죽은 sess-A가 레지스트리에 남아 있으면
        // 아래 진짜 끊김이 "다른 탭 생존"으로 오인돼 유예가 통째로 사라진다.
        room.getPlayers().add(new Player("p1", "나"));
        room.bindMember(PRINCIPAL, "p1");
        room.setBlackPrincipal(PRINCIPAL);
        room.setPlaying(true);
        listener.registerSession(PRINCIPAL, ROOM_ID, "sess-B");

        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-B"));

        verify(roomBroadcaster, times(1)).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.DISCONNECTED));
    }

    /**
     * 세션 키의 principal 절반을 고정한다. 같은 방에 상대(다른 principal)가 접속해 있어도
     * 내 마지막 세션이 끊기면 유예는 정상적으로 시작돼야 한다.
     * 키가 roomId만이면 상대 세션이 "내 다른 탭"으로 오인돼 유예가 통째로 사라진다.
     */
    @Test
    void 같은_방에_있는_다른_사용자의_세션은_내_유예를_막지_않는다() {
        listener.registerSession(PRINCIPAL, ROOM_ID, "sess-A");
        listener.registerSession("user:3", ROOM_ID, "sess-C");

        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-A"));

        verify(roomBroadcaster, times(1)).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.DISCONNECTED));
    }

    /**
     * 한 세션이 방 A로 JOIN한 뒤 존재하지 않는 방으로 다시 JOIN하면 attrs.roomId가 덮인다.
     * 소켓 종료 시 해제되는 키는 마지막 attrs.roomId 하나뿐이라, 방 A 키에 죽은 세션이 영구히 남는다.
     * 그러면 이후 방 A에서의 진짜 끊김이 "다른 탭 생존"으로 오인돼 유예/몰수가 영영 발동하지 않는다.
     */
    @Test
    void 다른_방으로_재JOIN해도_이전_방에_죽은_세션이_남지_않는다() {
        RoomSocketController controller = new RoomSocketController(
                roomBroadcaster, mock(RoomSocketService.class), listener);

        Map<String, Object> attrs = new HashMap<>();
        Principal user = () -> PRINCIPAL;

        controller.joinRoom(joinMessage(ROOM_ID), joinAccessor("sess-A", attrs), user);
        controller.joinRoom(joinMessage("junk"), joinAccessor("sess-A", attrs), user);

        // 소켓 종료. 핸들러가 보는 attrs.roomId는 "junk"뿐이다.
        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-A", "junk"));

        // 같은 사용자가 방 A에서 새 세션으로 플레이하다 진짜로 끊긴다.
        listener.registerSession(PRINCIPAL, ROOM_ID, "sess-B");
        listener.handleWebSocketDisconnectListener(disconnectEvent("sess-B", ROOM_ID));

        verify(roomBroadcaster, times(1)).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.DISCONNECTED));
    }
}

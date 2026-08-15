package org.scoula.room;

import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.scoula.room.service.GameService;
import org.scoula.room.service.RoomBroadcaster;
import org.scoula.room.service.RoomService;
import org.scoula.room.service.RoomServiceImpl;
import org.scoula.room.service.RoomSocketService;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 방 멤버십·자리 소유가 payload id가 아니라 인증 principal로 기록되는지 검증한다 (Task 2).
 */
class RoomMembershipTest {

    private Room emptyRoom() {
        return Room.builder()
                .roomId("room-1")
                .players(new java.util.concurrent.CopyOnWriteArrayList<>())
                .turn(1)
                .board(new int[15][15])
                .build();
    }

    @Test
    void bindsMemberByPrincipal_notPayloadId() {
        Room r = emptyRoom();
        r.bindMember("user:2", "uuid-A", "흑돌");
        r.bindMember("guest:x", "uuid-B", "백돌");

        assertTrue(r.isMember("user:2"));
        assertTrue(r.isMember("guest:x"));
        // payload로 온 표시용 id는 신원이 아니다.
        assertFalse(r.isMember("uuid-A"));
        assertFalse(r.isMember("intruder"));
        assertFalse(r.isMember(null));
    }

    @Test
    void bindMemberIsCappedAtTwoSeats() {
        Room r = emptyRoom();
        r.bindMember("user:1", "uuid-A", "A");
        r.bindMember("user:2", "uuid-B", "B");
        r.bindMember("user:3", "uuid-C", "C"); // 초과 → 무시

        assertTrue(r.isMember("user:1"));
        assertTrue(r.isMember("user:2"));
        assertFalse(r.isMember("user:3"));
    }

    @Test
    void attackerCannotStealSeatByReusingVictimPlayerId() {
        Room r = emptyRoom();
        r.bindMember("user:victim", "vid", "피해자");
        // 공격자가 피해자의 표시용 player.id를 그대로 실어 join.
        r.bindMember("user:attacker", "vid", "공격자");

        // 피해자는 여전히 멤버(락아웃/탈취 없음).
        assertTrue(r.isMember("user:victim"));
        // 공격자는 오직 자기 principal로만 멤버.
        assertTrue(r.isMember("user:attacker"));
        // 표시용 player.id는 신원이 아니다.
        assertFalse(r.isMember("vid"));
    }

    @Test
    void turnOwnerMatchesSeatPrincipal() {
        Player p1 = new Player("uuid-A", "흑돌");
        Player p2 = new Player("uuid-B", "백돌");

        Room room = Room.builder()
                .roomId("room-1")
                .players(new java.util.concurrent.CopyOnWriteArrayList<>(List.of(p1, p2)))
                .turn(1)
                .board(new int[15][15])
                .isPlaying(false)
                .build();
        room.bindMember("user:2", "uuid-A", "흑돌");
        room.bindMember("guest:x", "uuid-B", "백돌");

        RoomService roomService = mock(RoomService.class);
        when(roomService.getRoom("room-1")).thenReturn(room);
        RoomSocketService service = new RoomSocketService(
                mock(RoomBroadcaster.class), roomService, mock(GameService.class),
                mock(org.scoula.game.GameArchiveService.class), mock(TaskScheduler.class));

        // 게임 시작 → 두 멤버 principal 중 하나가 흑, 다른 하나가 백 자리 소유.
        service.notifyGameStart("room-1");

        String black = room.blackPrincipal();
        String white = room.whitePrincipal();
        assertNotNull(black);
        assertNotNull(white);
        assertEquals(Set.of("user:2", "guest:x"), Set.of(black, white));

        // turn 홀수=흑 자리, 짝=백 자리.
        assertTrue(room.isTurnOwner(black, 1));
        assertFalse(room.isTurnOwner(white, 1));
        assertTrue(room.isTurnOwner(white, 2));
        assertFalse(room.isTurnOwner(black, 2));

        // 비멤버는 어떤 턴에서도 소유자가 아니다.
        assertFalse(room.isTurnOwner("intruder", 1));
        assertFalse(room.isTurnOwner("intruder", 2));
    }

    @Test
    void unbindMemberClearsSeatAndTurnOwnership() {
        Room r = emptyRoom();
        r.bindMember("user:1", "uuid-A", "A");
        r.bindMember("user:2", "uuid-B", "B");
        r.setBlackPrincipal("user:1");
        r.setWhitePrincipal("user:2");

        r.unbindMember("user:1");

        assertFalse(r.isMember("user:1"));
        assertTrue(r.isMember("user:2"));
        // 흑 자리 소유자였다면 자리도 함께 비운다(유령이 턴 소유자로 남지 않도록).
        assertNull(r.blackPrincipal());
        assertEquals("user:2", r.whitePrincipal());
    }

    @Test
    void duplicatePlayerIdJoinIsRejected() {
        RoomServiceImpl svc = new RoomServiceImpl();
        Room room = svc.createRoom("t", null);
        String rid = room.getRoomId();

        assertEquals(1, svc.joinRoom(rid, new Player("vid", "피해자"), null, "user:victim"));
        // 공격자: 같은 표시용 player.id, 다른 name, 다른 principal → 거부(자리 오염 차단).
        assertEquals(0, svc.joinRoom(rid, new Player("vid", "공격자"), null, "user:attacker"));

        assertTrue(room.isMember("user:victim"));
        assertFalse(room.isMember("user:attacker"));
        assertEquals(1, room.getPlayers().size());
    }

    /**
     * 리뷰 fix: leaveRoom이 players 리스트에서만 지우고 playerIdByPrincipal 맵 엔트리를
     * 안 지우면, 떠난 principal이 유령 멤버로 남아 bindMember의 2자리 캡이 새 입장자를
     * 영구 거부하는 소프트락이 생긴다. HTTP leave와 grace-expire(WebSocketEventListener)가
     * 둘 다 RoomService.leaveRoom(roomId, playerId)를 그대로 호출하므로, 이 서비스 레벨
     * 테스트 하나로 두 경로 모두 검증된다.
     */
    @Test
    void leaveUnbindsMember_allowingNewPlayerToJoin() {
        RoomServiceImpl svc = new RoomServiceImpl();
        Room room = svc.createRoom("t", null);
        String rid = room.getRoomId();

        assertEquals(1, svc.joinRoom(rid, new Player("pA", "A"), null, "user:A"));
        assertEquals(1, svc.joinRoom(rid, new Player("pB", "B"), null, "user:B"));
        assertTrue(room.isMember("user:A"));
        assertTrue(room.isMember("user:B"));

        assertTrue(svc.leaveRoom(rid, "pA"));

        // (a) 떠난 principal은 더 이상 멤버가 아니다(유령 멤버 방지).
        assertFalse(room.isMember("user:A"));
        assertTrue(room.isMember("user:B"));

        // (b) 자리가 실제로 비어야 새 입장자가 2자리 캡에 안 걸리고 들어올 수 있다.
        assertEquals(1, svc.joinRoom(rid, new Player("pC", "C"), null, "user:C"));
        assertTrue(room.isMember("user:C"));
    }

    @Test
    void gameStartAlwaysAssignsTwoDistinctSeatPrincipals() {
        Player p1 = new Player("uuid-A", "A");
        Player p2 = new Player("uuid-B", "B");
        Room room = Room.builder()
                .roomId("room-1")
                .players(new java.util.concurrent.CopyOnWriteArrayList<>(List.of(p1, p2)))
                .turn(1)
                .board(new int[15][15])
                .build();
        room.bindMember("user:1", "uuid-A", "A");
        room.bindMember("user:2", "uuid-B", "B");

        RoomService roomService = mock(RoomService.class);
        when(roomService.getRoom("room-1")).thenReturn(room);
        RoomSocketService service = new RoomSocketService(
                mock(RoomBroadcaster.class), roomService, mock(GameService.class),
                mock(org.scoula.game.GameArchiveService.class), mock(TaskScheduler.class));

        // 랜덤 배정을 여러 번 돌려도 흑≠백 principal 불변.
        for (int i = 0; i < 30; i++) {
            service.notifyGameStart("room-1");
            assertNotNull(room.blackPrincipal());
            assertNotNull(room.whitePrincipal());
            assertNotEquals(room.blackPrincipal(), room.whitePrincipal());
        }
    }
}

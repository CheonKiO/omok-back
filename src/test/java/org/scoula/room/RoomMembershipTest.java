package org.scoula.room;

import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.scoula.room.service.GameService;
import org.scoula.room.service.RoomBroadcaster;
import org.scoula.room.service.RoomService;
import org.scoula.room.service.RoomSocketService;

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
                mock(RoomBroadcaster.class), roomService, mock(GameService.class));

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
}

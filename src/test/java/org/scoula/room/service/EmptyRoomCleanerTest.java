package org.scoula.room.service;

import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 빈 방 TTL GC 검증 (#6): 플레이어가 없고 생성 후 TTL이 지난 방만 제거되고,
 * 활성 방(플레이어 있음)과 최근 생성된 빈 방은 보존된다.
 * createRoom은 players가 빈 채로 방을 만들므로(아무도 join 안 하면 영구 잔존), 이것이 GC 대상이다.
 */
class EmptyRoomCleanerTest {

    @Test
    void removesEmptyRoomPastTtl() {
        RoomServiceImpl svc = new RoomServiceImpl();
        EmptyRoomCleaner cleaner = new EmptyRoomCleaner(svc);

        Room oldEmpty = svc.createRoom("old-empty", null);
        oldEmpty.setCreatedAt(System.currentTimeMillis() - (EmptyRoomCleaner.TTL_MILLIS + 60_000));

        cleaner.cleanup();

        assertNull(svc.getRoom(oldEmpty.getRoomId()), "TTL 경과한 빈 방은 제거되어야 한다");
    }

    @Test
    void preservesRecentEmptyRoom() {
        RoomServiceImpl svc = new RoomServiceImpl();
        EmptyRoomCleaner cleaner = new EmptyRoomCleaner(svc);

        Room recentEmpty = svc.createRoom("recent-empty", null); // createdAt = now

        cleaner.cleanup();

        assertNotNull(svc.getRoom(recentEmpty.getRoomId()), "최근 생성된 빈 방은 보존되어야 한다");
    }

    @Test
    void preservesActiveRoomEvenIfOld() {
        RoomServiceImpl svc = new RoomServiceImpl();
        EmptyRoomCleaner cleaner = new EmptyRoomCleaner(svc);

        Room active = svc.createRoom("active", null);
        active.getPlayers().add(new Player("p-1", "플레이어"));
        // 오래됐어도 플레이어가 있으면 활성 방 → 보존
        active.setCreatedAt(System.currentTimeMillis() - (EmptyRoomCleaner.TTL_MILLIS + 60_000));

        cleaner.cleanup();

        assertNotNull(svc.getRoom(active.getRoomId()), "플레이어가 있는 방은 오래됐어도 보존되어야 한다");
    }
}

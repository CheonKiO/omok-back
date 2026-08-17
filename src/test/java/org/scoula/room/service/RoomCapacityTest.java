package org.scoula.room.service;

import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Room;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 전체 방 수 상한 검증 (#23): 상한에 도달하면 createRoom이 null을 반환하고 isAtCapacity가 true다.
 * 게스트 무한 발급으로 방을 무한 누적시키는 DoS의 OOM 최후 방어선.
 */
class RoomCapacityTest {

    @Test
    void createRoomIsRejectedAtCapacity() {
        RoomServiceImpl svc = new RoomServiceImpl();

        for (int i = 0; i < RoomServiceImpl.MAX_ROOMS; i++) {
            assertNotNull(svc.createRoom("r" + i, null), "상한 전까지는 생성 성공");
        }

        assertTrue(svc.isAtCapacity(), "상한 도달");
        assertNull(svc.createRoom("overflow", null), "상한 초과 생성은 null");
    }

    @Test
    void notAtCapacityWhenBelowLimit() {
        RoomServiceImpl svc = new RoomServiceImpl();
        Room r = svc.createRoom("one", null);
        assertNotNull(r);
        assertFalse(svc.isAtCapacity());
    }
}

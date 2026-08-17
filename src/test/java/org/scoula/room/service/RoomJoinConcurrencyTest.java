package org.scoula.room.service;

import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * joinRoom의 2자리 캡이 원자적인지 검증 (#24).
 * 자리 하나 남은 방에 두 요청이 동시에 들어와도 3명이 앉으면 안 된다.
 * 락이 없으면 둘 다 size()==2 검사(1명 상태)를 통과해 add를 두 번 해 players가 3이 된다.
 */
class RoomJoinConcurrencyTest {

    @Test
    void concurrentJoinsDoNotExceedTwoSeats() throws InterruptedException {
        RoomServiceImpl svc = new RoomServiceImpl();
        Room room = svc.createRoom("t", null);
        String rid = room.getRoomId();

        // 이미 한 자리 참
        assertEquals(1, svc.joinRoom(rid, new Player("pA", "A"), null, "user:A"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();

        Runnable joinB = joiner(svc, rid, new Player("pB", "B"), "user:B", ready, fire, done, successes);
        Runnable joinC = joiner(svc, rid, new Player("pC", "C"), "user:C", ready, fire, done, successes);

        new Thread(joinB).start();
        new Thread(joinC).start();

        ready.await();
        fire.countDown();
        done.await();

        assertEquals(2, room.getPlayers().size(), "동시 입장에도 자리는 최대 2개여야 한다");
        assertEquals(1, successes.get(), "빈 자리 하나에 정확히 한 명만 성공해야 한다");
    }

    private Runnable joiner(RoomServiceImpl svc, String rid, Player p, String principal,
                            CountDownLatch ready, CountDownLatch fire, CountDownLatch done,
                            AtomicInteger successes) {
        return () -> {
            ready.countDown();
            try {
                fire.await();
                if (svc.joinRoom(rid, p, null, principal) == 1) successes.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };
    }
}

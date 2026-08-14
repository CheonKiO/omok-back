package org.scoula.room.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 방 생성 레이트리밋 검증 (#6): principal당 분당 N회를 넘는 생성 요청은 거부(false)된다.
 * 시간은 주입한 MutableClock으로 제어하여 윈도우 경계·리셋을 결정론적으로 검증한다.
 */
class RoomCreationRateLimiterTest {

    /** 테스트 제어용 가변 시계. */
    private static final class MutableClock extends Clock {
        private long millis;
        MutableClock(long start) { this.millis = start; }
        void advance(long ms) { this.millis += ms; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public long millis() { return millis; }
    }

    @Test
    void allowsUpToLimitThenRejects() {
        MutableClock clock = new MutableClock(1_000_000);
        RoomCreationRateLimiter limiter = new RoomCreationRateLimiter(clock);

        for (int i = 0; i < RoomCreationRateLimiter.MAX_PER_WINDOW; i++) {
            assertTrue(limiter.tryAcquire("user:A"), "한도 내 " + (i + 1) + "번째 요청은 허용되어야 한다");
        }
        // 한도 초과 → 거부
        assertFalse(limiter.tryAcquire("user:A"), "한도 초과 요청은 거부되어야 한다");
        assertFalse(limiter.tryAcquire("user:A"), "초과 상태가 유지되는 동안 계속 거부되어야 한다");
    }

    @Test
    void limitIsPerPrincipal() {
        MutableClock clock = new MutableClock(0);
        RoomCreationRateLimiter limiter = new RoomCreationRateLimiter(clock);

        for (int i = 0; i < RoomCreationRateLimiter.MAX_PER_WINDOW; i++) {
            limiter.tryAcquire("user:A");
        }
        assertFalse(limiter.tryAcquire("user:A"), "A는 한도 소진");
        // 다른 principal은 독립된 한도를 가진다
        assertTrue(limiter.tryAcquire("user:B"), "다른 principal은 영향받지 않아야 한다");
    }

    @Test
    void windowResetsAfterOneMinute() {
        MutableClock clock = new MutableClock(0);
        RoomCreationRateLimiter limiter = new RoomCreationRateLimiter(clock);

        for (int i = 0; i < RoomCreationRateLimiter.MAX_PER_WINDOW; i++) {
            limiter.tryAcquire("user:A");
        }
        assertFalse(limiter.tryAcquire("user:A"), "윈도우 내에서는 초과 거부");

        // 1분 경과 → 윈도우 리셋, 다시 허용
        clock.advance(60_000);
        assertTrue(limiter.tryAcquire("user:A"), "윈도우 경과 후에는 다시 허용되어야 한다");
    }

    @Test
    void nullPrincipalIsRejected() {
        RoomCreationRateLimiter limiter = new RoomCreationRateLimiter(Clock.systemUTC());
        assertFalse(limiter.tryAcquire(null), "principal이 없으면 거부");
    }
}

package org.scoula.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IP 인증 레이트리밋 검증 (#23): IP당 분당 N회를 넘는 요청은 거부되고, 윈도우가 지나면 리셋된다.
 */
class IpRateLimiterTest {

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
        IpRateLimiter limiter = new IpRateLimiter(clock, 3);

        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"), "한도 초과는 거부");
    }

    @Test
    void separateIpsHaveSeparateBudgets() {
        IpRateLimiter limiter = new IpRateLimiter(new MutableClock(0), 1);
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("2.2.2.2"), "다른 IP는 별도 한도");
        assertFalse(limiter.tryAcquire("1.1.1.1"));
    }

    @Test
    void windowResetsAfterExpiry() {
        MutableClock clock = new MutableClock(0);
        IpRateLimiter limiter = new IpRateLimiter(clock, 1);
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));

        clock.advance(IpRateLimiter.WINDOW_MILLIS);
        assertTrue(limiter.tryAcquire("1.1.1.1"), "윈도우 경과 후 리셋");
    }

    @Test
    void nullIpIsRejected() {
        assertFalse(new IpRateLimiter(new MutableClock(0), 10).tryAcquire(null));
    }
}

package org.scoula.room.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 방 생성 DoS 방지 (#6): 인증 principal당 고정 윈도우(1분)로 생성 횟수를 제한한다.
 * in-memory 카운터라 단일 인스턴스 기준이며, 초과 시 tryAcquire가 false를 반환한다.
 */
@Component
public class RoomCreationRateLimiter {

    /** principal당 윈도우 내 허용 생성 횟수. */
    public static final int MAX_PER_WINDOW = 10;
    /** 고정 윈도우 길이(ms). */
    static final long WINDOW_MILLIS = 60_000;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RoomCreationRateLimiter() {
        this(Clock.systemUTC());
    }

    // 테스트에서 시간을 주입하기 위한 생성자. Spring은 기본 생성자를 사용한다.
    RoomCreationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** principal의 이번 요청을 허용하면 true. 윈도우 한도를 넘으면 false. principal이 없으면 false. */
    public boolean tryAcquire(String principal) {
        if (principal == null) return false;
        long now = clock.millis();
        boolean[] allowed = new boolean[1];
        // compute 리매핑은 키 단위로 원자적으로 실행되어 카운트 증가/판정이 경합 없이 이뤄진다.
        windows.compute(principal, (key, current) -> {
            Window window = (current == null || now - current.windowStart >= WINDOW_MILLIS)
                    ? new Window(now)
                    : current;
            window.count++;
            allowed[0] = window.count <= MAX_PER_WINDOW;
            return window;
        });
        return allowed[0];
    }

    private static final class Window {
        final long windowStart;
        int count;
        Window(long windowStart) { this.windowStart = windowStart; }
    }
}

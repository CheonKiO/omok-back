package org.scoula.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인증 엔드포인트(guest/login/signup) IP당 고정 윈도우 레이트리밋 (#23).
 * 게스트 토큰을 무제한 발급받아 principal 기반 방생성 제한을 우회하는 것을 IP 단에서 막는다.
 * in-memory·단일 인스턴스 기준. 만료 윈도우는 접근 시 기회적으로 정리해 맵 카디널리티가
 * 무한 증가하지 않게 한다.
 */
@Component
public class IpRateLimiter {

    /** IP당 윈도우 내 허용 인증 요청 수 기본값. 테스트 프로파일은 크게 덮어써 트립을 피한다. */
    public static final int DEFAULT_MAX_PER_WINDOW = 30;
    /** 고정 윈도우 길이(ms). */
    static final long WINDOW_MILLIS = 60_000;
    /** 이 크기를 넘으면 만료 항목을 정리한다(카디널리티 상한 방어). */
    static final int PURGE_THRESHOLD = 10_000;

    private final Clock clock;
    private final int maxPerWindow;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public IpRateLimiter(@Value("${auth.ratelimit.max-per-window:30}") int maxPerWindow) {
        this(Clock.systemUTC(), maxPerWindow);
    }

    IpRateLimiter(Clock clock, int maxPerWindow) {
        this.clock = clock;
        this.maxPerWindow = maxPerWindow;
    }

    /** 이 IP의 이번 요청을 허용하면 true. 윈도우 한도를 넘으면 false. */
    public boolean tryAcquire(String ip) {
        if (ip == null) return false;
        long now = clock.millis();
        if (windows.size() > PURGE_THRESHOLD) purgeExpired(now);
        boolean[] allowed = new boolean[1];
        windows.compute(ip, (key, current) -> {
            Window window = (current == null || now - current.windowStart >= WINDOW_MILLIS)
                    ? new Window(now)
                    : current;
            window.count++;
            allowed[0] = window.count <= maxPerWindow;
            return window;
        });
        return allowed[0];
    }

    private void purgeExpired(long now) {
        for (Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator(); it.hasNext(); ) {
            if (now - it.next().getValue().windowStart >= WINDOW_MILLIS) it.remove();
        }
    }

    private static final class Window {
        final long windowStart;
        int count;
        Window(long windowStart) { this.windowStart = windowStart; }
    }
}

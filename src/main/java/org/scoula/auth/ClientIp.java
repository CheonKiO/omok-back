package org.scoula.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 리버스 프록시(Apache/nginx) 뒤에서 실제 클라이언트 IP를 해석한다.
 * X-Forwarded-For의 첫 홉(클라이언트) → X-Real-IP → getRemoteAddr 순.
 * 프록시가 헤더를 신뢰 가능하게 세팅한다는 전제(직접 노출 서버라면 스푸핑 가능하니 주의).
 */
public final class ClientIp {

    private ClientIp() {}

    public static String resolve(HttpServletRequest request) {
        if (request == null) return "unknown";
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // "client, proxy1, proxy2" → 첫 항목이 원 클라이언트.
            int comma = xff.indexOf(',');
            String first = (comma >= 0 ? xff.substring(0, comma) : xff).trim();
            if (!first.isEmpty()) return first;
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}

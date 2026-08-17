package org.scoula.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 프록시 뒤 클라이언트 IP 해석 검증 (#23).
 */
class ClientIpTest {

    @Test
    void prefersFirstHopOfXForwardedFor() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 10.0.0.2");
        req.setRemoteAddr("127.0.0.1");
        assertEquals("203.0.113.7", ClientIp.resolve(req));
    }

    @Test
    void fallsBackToXRealIp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Real-IP", "198.51.100.9");
        req.setRemoteAddr("127.0.0.1");
        assertEquals("198.51.100.9", ClientIp.resolve(req));
    }

    @Test
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.0.2.5");
        assertEquals("192.0.2.5", ClientIp.resolve(req));
    }
}

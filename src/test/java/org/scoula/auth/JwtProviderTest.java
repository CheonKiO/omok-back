package org.scoula.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.scoula.user.Role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long!!";
    private final JwtProvider jwt = new JwtProvider(SECRET, 1_800_000, 1_209_600_000);

    @Test
    void accessTokenRoundTripsSubjectAndRole() {
        String token = jwt.createAccessToken("42", Role.USER);
        assertEquals("42", jwt.getSubject(token));
        assertEquals(Role.USER, jwt.getRole(token));
    }

    @Test
    void guestRolePreservedInToken() {
        String token = jwt.createAccessToken("guest-uuid", Role.GUEST);
        assertEquals(Role.GUEST, jwt.getRole(token));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtProvider shortLived = new JwtProvider(SECRET, -1_000, -1_000);
        String token = shortLived.createAccessToken("1", Role.USER);
        assertThrows(JwtException.class, () -> shortLived.getSubject(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.createAccessToken("1", Role.USER);
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThrows(JwtException.class, () -> jwt.getSubject(tampered));
    }
}

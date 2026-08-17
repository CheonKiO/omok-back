package org.scoula.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.scoula.user.Role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void accessTokenIsTypedAccess() {
        String token = jwt.createAccessToken("42", Role.USER);
        assertEquals(JwtProvider.TYPE_ACCESS, jwt.getType(token));
        assertTrue(jwt.isAccessToken(token));
    }

    @Test
    void refreshTokenIsNotAccessType() {
        String token = jwt.createRefreshToken("42");
        assertEquals(JwtProvider.TYPE_REFRESH, jwt.getType(token));
        assertFalse(jwt.isAccessToken(token), "refresh 토큰은 access로 인정되면 안 된다");
    }

    @Test
    void blankSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtProvider("", 1000, 1000));
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtProvider("too-short", 1000, 1000));
    }
}

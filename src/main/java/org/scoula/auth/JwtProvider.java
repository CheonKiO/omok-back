package org.scoula.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.scoula.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessExpMillis;
    private final long refreshExpMillis;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-exp-millis:1800000}") long accessExpMillis,
            @Value("${jwt.refresh-exp-millis:1209600000}") long refreshExpMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpMillis = accessExpMillis;
        this.refreshExpMillis = refreshExpMillis;
    }

    public String createAccessToken(String subject, Role role) {
        return build(subject, role, accessExpMillis);
    }

    public String createRefreshToken(String subject) {
        return build(subject, null, refreshExpMillis);
    }

    private String build(String subject, Role role, long expMillis) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expMillis));
        if (role != null) {
            builder.claim("role", role.name());
        }
        return builder.signWith(key).compact();
    }

    // 서명·만료 검증 후 payload 반환. 유효하지 않으면 JwtException.
    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getSubject(String token) {
        return parse(token).getSubject();
    }

    public Role getRole(String token) {
        String role = parse(token).get("role", String.class);
        return role == null ? null : Role.valueOf(role);
    }
}

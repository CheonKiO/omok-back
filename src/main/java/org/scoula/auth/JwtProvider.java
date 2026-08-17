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

    // 토큰 종류(typ 클레임). access만 인가에 쓰이고, refresh는 /refresh에서만 소비된다.
    // 이 클레임이 없으면 refresh 토큰을 Bearer로 보내 보호 엔드포인트를 통과할 수 있다.
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final int MIN_SECRET_BYTES = 32; // HMAC-SHA256

    private final SecretKey key;
    private final long accessExpMillis;
    private final long refreshExpMillis;

    public JwtProvider(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.access-exp-millis:1800000}") long accessExpMillis,
            @Value("${jwt.refresh-exp-millis:1209600000}") long refreshExpMillis) {
        // 커밋된 약한 기본키를 없앤다. 미설정/약한 키로는 부팅을 실패시켜(fail-fast),
        // prod가 조용히 취약한 상태로 뜨는 것을 막는다. 로컬/CI는 JWT_SECRET 또는
        // application-test.yml로 주입한다.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret(환경변수 JWT_SECRET)를 최소 " + MIN_SECRET_BYTES + "바이트 이상으로 설정해야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpMillis = accessExpMillis;
        this.refreshExpMillis = refreshExpMillis;
    }

    public String createAccessToken(String subject, Role role) {
        return build(subject, role, TYPE_ACCESS, accessExpMillis);
    }

    public String createRefreshToken(String subject) {
        return build(subject, null, TYPE_REFRESH, refreshExpMillis);
    }

    private String build(String subject, Role role, String type, long expMillis) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(subject)
                .claim("typ", type)
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

    /** 토큰의 typ 클레임. 서명·만료 검증 실패 시 JwtException. */
    public String getType(String token) {
        return parse(token).get("typ", String.class);
    }

    /** access 토큰만 인가에 허용한다(refresh를 Bearer로 재사용하지 못하게). */
    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(getType(token));
    }

    public java.time.LocalDateTime getExpiration(String token) {
        Date exp = parse(token).getExpiration();
        return java.time.LocalDateTime.ofInstant(exp.toInstant(), java.time.ZoneId.systemDefault());
    }
}

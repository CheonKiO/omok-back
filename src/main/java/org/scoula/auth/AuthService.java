package org.scoula.auth;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.scoula.user.Role;
import org.scoula.user.User;
import org.scoula.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public User signup(String username, String rawPassword, String nickname) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .nickname(nickname)
                .role(Role.USER)
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public TokenPair login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String subject = String.valueOf(user.getId());
        String access = jwtProvider.createAccessToken(subject, user.getRole());
        String refresh = jwtProvider.createRefreshToken(subject);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .token(refresh)
                .expiresAt(jwtProvider.getExpiration(refresh))
                .build());
        return new TokenPair(access, refresh);
    }

    // 게스트: DB 저장 없이 GUEST 역할의 단기 access 토큰만 발급.
    public String guestToken(String nickname) {
        String guestId = "guest-" + UUID.randomUUID();
        return jwtProvider.createAccessToken(guestId, Role.GUEST);
    }

    @Transactional(readOnly = true)
    public TokenPair refresh(String refreshToken) {
        String subject;
        try {
            subject = jwtProvider.getSubject(refreshToken); // 서명·만료 검증
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
        // DB에 존재해야 유효 (로그아웃/폐기된 토큰 차단)
        refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(InvalidTokenException::new);
        User user = userRepository.findById(Long.valueOf(subject))
                .orElseThrow(InvalidTokenException::new);
        String access = jwtProvider.createAccessToken(subject, user.getRole());
        return new TokenPair(access, refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }
}

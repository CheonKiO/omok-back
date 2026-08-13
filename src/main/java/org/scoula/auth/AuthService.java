package org.scoula.auth;

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

    @Transactional(readOnly = true)
    public TokenPair login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String subject = String.valueOf(user.getId());
        return new TokenPair(
                jwtProvider.createAccessToken(subject, user.getRole()),
                jwtProvider.createRefreshToken(subject));
    }

    // 게스트: DB 저장 없이 GUEST 역할의 단기 access 토큰만 발급.
    public String guestToken(String nickname) {
        String guestId = "guest-" + UUID.randomUUID();
        return jwtProvider.createAccessToken(guestId, Role.GUEST);
    }
}

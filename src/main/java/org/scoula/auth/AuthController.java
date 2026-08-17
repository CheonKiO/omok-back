package org.scoula.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scoula.auth.dto.GuestRequest;
import org.scoula.auth.dto.LoginRequest;
import org.scoula.auth.dto.RefreshRequest;
import org.scoula.auth.dto.SignupRequest;
import org.scoula.auth.dto.TokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final IpRateLimiter ipRateLimiter;

    // 인증 엔드포인트 IP 레이트리밋. 게스트 토큰 무제한 발급(방생성 제한 우회)과
    // 로그인 브루트포스를 IP 단에서 완화한다. 초과 시 429.
    private void enforceIpLimit(HttpServletRequest request) {
        String ip = ClientIp.resolve(request);
        if (!ipRateLimiter.tryAcquire(ip)) {
            log.warn("[AUTH_RATELIMIT] ip={}", ip);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many auth requests. Try again later.");
        }
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
        enforceIpLimit(http);
        authService.signup(request.username(), request.password(), request.nickname());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        enforceIpLimit(http);
        TokenPair tokens = authService.login(request.username(), request.password());
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken());
    }

    @PostMapping("/guest")
    public TokenResponse guest(@Valid @RequestBody GuestRequest request, HttpServletRequest http) {
        enforceIpLimit(http);
        return new TokenResponse(authService.guestToken(request.nickname()), null);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPair tokens = authService.refresh(request.refreshToken());
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}

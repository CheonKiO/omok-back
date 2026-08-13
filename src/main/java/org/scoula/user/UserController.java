package org.scoula.user;

import org.scoula.auth.dto.MeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    // 인증된 요청만 도달 (SecurityConfig에서 /api/users/** 는 authenticated).
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .orElse(null);
        return new MeResponse(authentication.getName(), role);
    }
}

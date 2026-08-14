package org.scoula.config;

import lombok.RequiredArgsConstructor;
import org.scoula.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CORS 프리플라이트
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 인증 엔드포인트
                        .requestMatchers("/api/auth/**").permitAll()
                        // 방 목록/조회는 공개 (배포 헬스체크: 무인증 GET /api/rooms)
                        .requestMatchers(HttpMethod.GET, "/api/rooms", "/api/rooms/**").permitAll()
                        // 방 생성/입장/퇴장은 인증 필요
                        .requestMatchers(HttpMethod.POST, "/api/rooms/**").authenticated()
                        // 기존 게임 WebSocket — 현행 유지 (신원 강제는 STOMP 인터셉터가 담당)
                        .requestMatchers("/game/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 그 외는 인증 필요 (예: /api/users/me)
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

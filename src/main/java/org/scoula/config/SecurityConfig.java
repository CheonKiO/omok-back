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
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
                .cors(Customizer.withDefaults())
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

    // CORS를 Security 필터체인에 등록한다. MVC 레벨(WebMvcConfig)에만 두면 JWT 필터가
    // 컨트롤러 도달 전 401을 반환할 때 CORS 헤더가 빠져 브라우저가 응답을 차단하고,
    // 프론트 인터셉터가 401을 인지하지 못해 토큰 refresh가 트리거되지 않는다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "https://cheonkio.github.io"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

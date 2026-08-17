package org.scoula.auth;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.scoula.user.Role;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

// STOMP CONNECT 시 Authorization 헤더의 JWT를 검증해 세션 principal로 바인딩한다.
// 토큰이 없거나 유효하지 않으면 principal 미설정(현행 게임은 익명 연결 허용).
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtProvider jwtProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String bearer = accessor.getFirstNativeHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7);
            try {
                // access 토큰만 principal로 바인딩(refresh 토큰 재사용 차단).
                if (!jwtProvider.isAccessToken(token)) {
                    return message;
                }
                String subject = jwtProvider.getSubject(token);
                Role role = jwtProvider.getRole(token);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                accessor.setUser(new UsernamePasswordAuthenticationToken(subject, null, authorities));
            } catch (JwtException | IllegalArgumentException e) {
                // 유효하지 않은 토큰 → principal 미설정
            }
        }
        return message;
    }
}

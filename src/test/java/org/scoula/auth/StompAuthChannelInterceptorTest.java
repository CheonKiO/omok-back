package org.scoula.auth;

import org.junit.jupiter.api.Test;
import org.scoula.user.Role;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class StompAuthChannelInterceptorTest {

    private final JwtProvider jwtProvider = new JwtProvider(
            "test-secret-key-that-is-at-least-32-bytes-long!!", 1_800_000, 1_209_600_000);
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtProvider);
    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> connectMessage(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void bindsPrincipalFromValidBearerTokenOnConnect() {
        String token = jwtProvider.createAccessToken("42", Role.USER);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);

        interceptor.preSend(connectMessage(accessor), channel);

        assertEquals("42", accessor.getUser().getName());
    }

    @Test
    void leavesPrincipalUnsetWhenNoToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);

        interceptor.preSend(connectMessage(accessor), channel);

        assertNull(accessor.getUser());
    }

    @Test
    void leavesPrincipalUnsetWhenTokenInvalid() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer not-a-jwt");

        interceptor.preSend(connectMessage(accessor), channel);

        assertNull(accessor.getUser());
    }
}

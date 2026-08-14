package org.scoula.config;

import lombok.RequiredArgsConstructor;
import org.scoula.auth.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig  implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        //클라이언트 발행 시 사용하는 접두어. app이 관례
        config.setApplicationDestinationPrefixes("/app");
    }

    // STOMP inbound 채널에 JWT 인증 인터셉터 등록 (CONNECT 시 principal 바인딩)
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/game") // ws://localhost:8080/game
                .setAllowedOrigins("http://localhost:5173","https://cheonkio.github.io")
                .withSockJS();

    }
}

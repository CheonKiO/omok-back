package org.scoula.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // /api/** 만 CORS 매핑. /game(WebSocket)은 WebSocketConfig.setAllowedOrigins()가 담당하며
    // 두 곳에서 CORS 헤더가 중복되면 브라우저가 거부하므로 여기서 제외한다.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "https://cheonkio.github.io")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}

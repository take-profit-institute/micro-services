package org.profit.candle.chatting.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 방 배정 REST(/chat/rooms)용 CORS.
 *
 * <p>웹앱이 dev에서 chatting-service에 직결하므로(게이트웨이 우회) 자체 CORS가 필요하다.
 * 게이트웨이 CORS와 동일하게 {@code allowCredentials=true} + 오리진 패턴 방식을 쓴다
 * (와일드카드 + credentials 조합은 patterns로만 허용됨). WS 핸드셰이크는 브라우저 CORS 강제 대상이 아니다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(ChatProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.cors().allowedOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}

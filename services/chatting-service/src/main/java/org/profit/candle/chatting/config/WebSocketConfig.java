package org.profit.candle.chatting.config;

import java.util.Map;
import org.profit.candle.chatting.ws.ChatWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

/**
 * WebFlux WebSocket 라우팅: {@code /chat/ws} → {@link ChatWebSocketHandler}.
 *
 * <p>핸들러 매핑 order를 REST 라우트(기본 0)보다 앞(-1)에 둔다.
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping chatWebSocketMapping(ChatWebSocketHandler handler) {
        Map<String, WebSocketHandler> map = Map.of("/chat/ws", handler);
        return new SimpleUrlHandlerMapping(map, -1);
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}

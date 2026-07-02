package org.profit.candle.market.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketInitializer {

    private final KiwoomWebSocketClient webSocketClient;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        webSocketClient.connect(List.of(
                "005930",
                "000660",
                "035420"
        ));
    }
}

package org.profit.candle.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class KiwoomWebClientConfig {

    @Bean
    public WebClient kiwoomWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.kiwoom.com")
                .build();
    }
}

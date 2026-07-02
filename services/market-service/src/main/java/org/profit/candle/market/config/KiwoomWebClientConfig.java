package org.profit.candle.market.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class KiwoomWebClientConfig {

    @Bean
    public WebClient kiwoomWebClient(
            @Value("${kiwoom.base-url:https://api.kiwoom.com}") String baseUrl,
            @Value("${kiwoom.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${kiwoom.response-timeout-ms:5000}") long responseTimeoutMs,
            @Value("${kiwoom.read-timeout-ms:5000}") long readTimeoutMs,
            @Value("${kiwoom.write-timeout-ms:5000}") long writeTimeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

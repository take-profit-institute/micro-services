package org.profit.candle.news.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "naver.news")
public record NaverNewsProperties(
        String clientId,
        String clientSecret,
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public NaverNewsProperties {
        if (baseUrl == null) {
            baseUrl = URI.create("https://openapi.naver.com");
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}

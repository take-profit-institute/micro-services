package org.profit.candle.news.collector;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "news.collection")
public record NewsCollectionProperties(
        Duration requestDelay,
        int batchSize,
        Duration rateLimitBackoff,
        int maxConsecutiveRateLimit
) {
    public NewsCollectionProperties {
        if (requestDelay == null) {
            requestDelay = Duration.ofMillis(300);
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
        if (rateLimitBackoff == null) {
            rateLimitBackoff = Duration.ofSeconds(30);
        }
        if (maxConsecutiveRateLimit <= 0) {
            maxConsecutiveRateLimit = 3;
        }
    }
}

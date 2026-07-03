package org.profit.candle.news.stock;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "stock.grpc")
public record StockGrpcProperties(
        Duration deadline
) {
    public StockGrpcProperties {
        if (deadline == null) {
            deadline = Duration.ofSeconds(3);
        }
    }
}

package org.profit.candle.wishlist.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wishlist")
public record WishlistProperties(
        Alert alert,
        Market market,
        Notification notification
) {
    public record Alert(
            int thresholdBasisPoints,
            int retryBatchSize,
            Duration retryDelay
    ) {
    }

    public record Market(
            String quoteChannel,
            String timezone
    ) {
    }

    public record Notification(
            String grpcAddress,
            Duration deadline
    ) {
    }
}

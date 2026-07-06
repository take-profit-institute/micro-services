package org.profit.candle.ranking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ranking.grpc")
public record RankingGrpcProperties(Duration portfolioDeadline) {

    public RankingGrpcProperties {
        if (portfolioDeadline == null) {
            portfolioDeadline = Duration.ofSeconds(3);
        }
    }
}

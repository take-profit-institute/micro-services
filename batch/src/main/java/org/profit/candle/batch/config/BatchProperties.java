package org.profit.candle.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "batch")
public record BatchProperties(
        Schedule schedule
) {

    public record Schedule(
            String zoneId,
            Smoke smoke
    ) {
    }

    public record Smoke(
            boolean enabled,
            String cron
    ) {
    }
}
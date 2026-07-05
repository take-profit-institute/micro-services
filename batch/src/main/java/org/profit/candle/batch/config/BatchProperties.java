package org.profit.candle.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "batch")
public record BatchProperties(
        Schedule schedule,
        Grpc grpc
) {

    public record Schedule(
            String zoneId,
            Smoke smoke,
            PortfolioEod portfolioEod,
            StockSync stockSync
    ) {
    }

    public record Smoke(
            boolean enabled,
            String cron
    ) {
    }

    public record PortfolioEod(
            boolean enabled,
            String cron,
            int chunkSize,
            int symbolBatchSize
    ) {
    }

    public record StockSync(
            boolean enabled,
            String cron
    ) {
    }

    public record Grpc(
            String marketTarget,
            String stockTarget,
            String tradingTarget,
            String portfolioTarget,
            long readDeadlineMillis,
            long writeDeadlineMillis,
            long stockSyncDeadlineMillis,
            long tradingBatchDeadlineMillis
    ) {
    }
}

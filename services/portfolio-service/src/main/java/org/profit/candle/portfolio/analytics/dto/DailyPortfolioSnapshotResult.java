package org.profit.candle.portfolio.analytics.dto;

public record DailyPortfolioSnapshotResult(
        String userId,
        long totalAsset,
        String cumulativeReturnRate
) {}

package org.profit.candle.portfolio.analytics.dto;

public record PortfolioSnapshotResult(
        String date,
        long totalAsset,
        long stockValue,
        long dailyProfit,
        String cumulativeReturnRate
) {}

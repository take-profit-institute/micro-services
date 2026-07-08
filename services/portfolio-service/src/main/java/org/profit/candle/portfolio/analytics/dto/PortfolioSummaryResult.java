package org.profit.candle.portfolio.analytics.dto;

public record PortfolioSummaryResult(
        String userId,
        long totalBookValue,
        long totalStockValue,        // MarketService.BatchQuotes 현재가 기준
        long totalUnrealizedProfit,
        long totalRealizedProfit,
        String totalReturnRate,      // "%"
        String dayReturnRate,        // "%"
        long dayProfit,
        int holdingCount
) {}

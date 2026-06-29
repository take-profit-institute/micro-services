package org.profit.candle.portfolio.analytics.dto;

public record PortfolioSummaryResult(
        String userId,
        long totalBookValue,
        long totalStockValue,        // 캐시 시세 기준 근사값
        long totalUnrealizedProfit,
        long totalRealizedProfit,
        String totalReturnRate,      // "%"
        String dayReturnRate,        // "%"
        long dayProfit,
        int holdingCount
) {}

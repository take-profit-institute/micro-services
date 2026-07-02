package org.profit.candle.portfolio.analytics.dto;

public record SectorBreakdownResult(
        String sector,
        String weight,   // "%"
        long bookValue,
        int count
) {}

package org.profit.candle.portfolio.analytics.dto;

public record SectorBreakdownResult(
        String sector,
        String weight,   // "%"
        long bookValue,  // 호환성 유지 필드명. 값은 현재가 기준 평가금액.
        int count
) {}

package org.profit.candle.stock.chart.dto;

import java.time.Instant;

/** 종목 통계(52주 고저 + 최근 일봉). 데이터가 없으면 값은 0, {@code asOf} 는 null. */
public record PriceStatsResult(
        String code,
        long high,
        long low,
        long latestClose,
        long latestVolume,
        Instant asOf) {

    public static PriceStatsResult empty(String code) {
        return new PriceStatsResult(code, 0L, 0L, 0L, 0L, null);
    }
}

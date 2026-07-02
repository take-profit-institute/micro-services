package org.profit.candle.stock.chart.dto;

import java.time.Instant;
import java.util.List;

/** 한 종목의 sparkline 데이터. closes 는 오래된 -> 최신 순서다. */
public record SparklineResult(
        String code,
        List<Long> closes,
        Instant lastOpenTime) {
}

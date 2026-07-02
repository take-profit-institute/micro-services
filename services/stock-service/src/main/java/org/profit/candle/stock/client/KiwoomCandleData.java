package org.profit.candle.stock.client;

import org.profit.candle.stock.chart.dto.CandleInterval;

import java.time.Instant;

public record KiwoomCandleData(
        String code,
        CandleInterval interval,
        Instant openTime,
        long open,
        long high,
        long low,
        long close,
        long volume) {
}

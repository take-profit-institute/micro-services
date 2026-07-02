package org.profit.candle.stock.chart.dto;

import org.profit.candle.stock.chart.entity.CandleEntity;

import java.time.Instant;

public record CandleResult(
        String code,
        CandleInterval interval,
        Instant openTime,
        long open,
        long high,
        long low,
        long close,
        long volume,
        boolean closed) {

    public static CandleResult from(CandleEntity candle) {
        return new CandleResult(
                candle.id().stockCode(),
                CandleInterval.fromStorageValue(candle.id().interval()),
                candle.id().openTime(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                candle.closed());
    }
}

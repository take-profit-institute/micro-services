package org.profit.candle.stock.chart.service;

import org.profit.candle.stock.chart.dto.CandleInterval;

import java.time.Instant;

public interface CandleBackfillService {
    int backfill(String code, CandleInterval interval, int count, Instant to);
}

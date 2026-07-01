package org.profit.candle.stock.chart.service;

import org.profit.candle.stock.chart.dto.CandleInterval;

public interface CandleBackfillService {
    int backfill(String code, CandleInterval interval, int count);
}

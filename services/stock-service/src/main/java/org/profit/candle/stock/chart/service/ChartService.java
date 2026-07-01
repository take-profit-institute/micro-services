package org.profit.candle.stock.chart.service;

import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.dto.CandleResult;

import java.time.Instant;
import java.util.List;

public interface ChartService {
    List<CandleResult> getCandles(String code, CandleInterval interval, int limit, Instant to);
}

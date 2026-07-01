package org.profit.candle.stock.client;

import org.profit.candle.stock.chart.dto.CandleInterval;

import java.time.Instant;
import java.util.List;

public interface KiwoomChartClient {
    List<KiwoomCandleData> fetchCandles(String code, CandleInterval interval, int count, Instant to);
}

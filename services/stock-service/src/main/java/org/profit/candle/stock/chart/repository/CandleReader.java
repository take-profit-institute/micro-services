package org.profit.candle.stock.chart.repository;

import org.profit.candle.stock.chart.entity.CandleEntity;

import java.time.Instant;
import java.util.List;

public interface CandleReader {
    List<CandleEntity> findLatest(String stockCode, String interval, Instant to, int limit);
}

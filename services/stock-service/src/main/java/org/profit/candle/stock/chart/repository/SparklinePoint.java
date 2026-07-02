package org.profit.candle.stock.chart.repository;

import java.time.Instant;

/** {@code findRecentCloses} 결과 행. 종목별 최근 종가만 담는 경량 projection. */
public interface SparklinePoint {
    String getStockCode();

    Instant getOpenTime();

    long getClose();
}

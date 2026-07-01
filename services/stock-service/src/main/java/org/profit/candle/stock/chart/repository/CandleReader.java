package org.profit.candle.stock.chart.repository;

import org.profit.candle.stock.chart.entity.CandleEntity;

import java.time.Instant;
import java.util.List;

public interface CandleReader {
    List<CandleEntity> findLatest(String stockCode, String interval, Instant to, int limit);

    /** 여러 종목의 최근 {@code points} 개 종가를 한 번에 조회한다(오래된 -> 최신 정렬). */
    List<SparklinePoint> findRecentCloses(List<String> codes, String interval, int points);

    /** 특정 주기·시각의 아직 확정되지 않은(closed=false) 캔들들. EOD 마감 대상. */
    List<CandleEntity> findOpenAt(String interval, Instant openTime);
}

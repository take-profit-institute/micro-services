package org.profit.candle.stock.chart.repository;

import org.profit.candle.stock.chart.entity.CandleEntity;

import java.time.Instant;
import java.util.List;

public interface CandleReader {
    List<CandleEntity> findLatest(String stockCode, String interval, Instant to, int limit);

    /** 여러 종목의 최근 {@code points} 개 종가를 한 번에 조회한다(오래된 -> 최신 정렬). */
    List<SparklinePoint> findRecentCloses(List<String> codes, String interval, int points);

    /** 요청 종목 중 특정 주기·시각 캔들이 이미 존재하는 종목 코드. */
    List<String> findExistingCodesAt(List<String> codes, String interval, Instant openTime);

    /** {@code from} 이후 구간의 최고/최저가 집계(52주 고저 등). 데이터가 없으면 high/low 가 null 인 행을 돌려준다. */
    PriceStatsRow findPriceStats(String stockCode, String interval, Instant from);

    /** 특정 주기·시각의 아직 확정되지 않은(closed=false) 캔들들. EOD 마감 대상. */
    List<CandleEntity> findOpenAt(String interval, Instant openTime);
}

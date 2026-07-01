package org.profit.candle.stock.chart.repository;

import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.entity.CandleId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface JpaCandleRepository extends JpaRepository<CandleEntity, CandleId>, CandleReader, CandleWriter {

    @Override
    default List<CandleEntity> findLatest(String stockCode, String interval, Instant to, int limit) {
        var page = PageRequest.of(0, limit);
        // to 가 null 이면 상한 없이 최신부터. Instant 파라미터를 bare `IS NULL` 에 넣으면
        // PostgreSQL 이 타입을 추론하지 못해(42P18) 실패하므로 쿼리를 분리한다.
        return to == null
                ? findLatestPage(stockCode, interval, page)
                : findBeforePage(stockCode, interval, to, page);
    }

    @Query("""
            SELECT c FROM CandleEntity c
            WHERE c.id.stockCode = :stockCode
              AND c.id.interval = :interval
            ORDER BY c.id.openTime DESC
            """)
    List<CandleEntity> findLatestPage(@Param("stockCode") String stockCode,
                                      @Param("interval") String interval,
                                      org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT c FROM CandleEntity c
            WHERE c.id.stockCode = :stockCode
              AND c.id.interval = :interval
              AND c.id.openTime < :to
            ORDER BY c.id.openTime DESC
            """)
    List<CandleEntity> findBeforePage(@Param("stockCode") String stockCode,
                                      @Param("interval") String interval,
                                      @Param("to") Instant to,
                                      org.springframework.data.domain.Pageable pageable);
}

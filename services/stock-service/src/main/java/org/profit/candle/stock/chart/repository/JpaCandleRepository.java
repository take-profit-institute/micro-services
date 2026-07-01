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
    @Query("""
            SELECT c FROM CandleEntity c
            WHERE c.id.stockCode = :stockCode
              AND c.id.interval = :interval
              AND (:to IS NULL OR c.id.openTime < :to)
            ORDER BY c.id.openTime DESC
            """)
    default List<CandleEntity> findLatest(String stockCode, String interval, Instant to, int limit) {
        return findLatestPage(stockCode, interval, to, PageRequest.of(0, limit));
    }

    @Query("""
            SELECT c FROM CandleEntity c
            WHERE c.id.stockCode = :stockCode
              AND c.id.interval = :interval
              AND (:to IS NULL OR c.id.openTime < :to)
            ORDER BY c.id.openTime DESC
            """)
    List<CandleEntity> findLatestPage(@Param("stockCode") String stockCode,
                                      @Param("interval") String interval,
                                      @Param("to") Instant to,
                                      org.springframework.data.domain.Pageable pageable);
}

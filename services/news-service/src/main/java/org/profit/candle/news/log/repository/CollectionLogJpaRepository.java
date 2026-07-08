package org.profit.candle.news.log.repository;

import org.profit.candle.news.log.entity.CollectionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CollectionLogJpaRepository extends JpaRepository<CollectionLog, UUID> {
    boolean existsByMessageContaining(String marker);

    @Query("""
            SELECT COUNT(log) > 0
            FROM CollectionLog log
            WHERE log.collectedAt >= :from
              AND log.collectedAt < :to
              AND (log.message IS NULL OR log.message NOT LIKE '%catchUpSlot=%')
            """)
    boolean existsRegularCollectionBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}

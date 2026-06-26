package org.profit.candle.portfolio.analytics.repository;

import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JpaPortfolioSnapshotRepository
        extends JpaRepository<PortfolioSnapshotEntity, Long>, PortfolioSnapshotReader, PortfolioSnapshotWriter {

    @Override
    @Query("SELECT s FROM PortfolioSnapshotEntity s WHERE s.userId = :userId AND s.snapshotDate >= :from ORDER BY s.snapshotDate ASC")
    List<PortfolioSnapshotEntity> findByUserIdAfterDate(@Param("userId") String userId, @Param("from") LocalDate from);

    @Override
    @Query("SELECT s FROM PortfolioSnapshotEntity s WHERE s.userId = :userId AND s.snapshotDate = :date")
    Optional<PortfolioSnapshotEntity> findByUserIdAndDate(@Param("userId") String userId, @Param("date") LocalDate date);
}

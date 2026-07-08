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

    @Override
    @Query(value = """
            SELECT *
            FROM portfolio_snapshots s
            WHERE s.snapshot_date = :date
              AND (:lastUserId IS NULL OR s.user_id > :lastUserId)
            ORDER BY s.user_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<PortfolioSnapshotEntity> findDailySnapshotsAfterUserId(
            @Param("date") LocalDate date,
            @Param("lastUserId") String lastUserId,
            @Param("limit") int limit
    );

    @Override
    default Optional<PortfolioSnapshotEntity> findLatestByUserId(String userId) {
        return findFirstByUserIdOrderBySnapshotDateDesc(userId);
    }

    Optional<PortfolioSnapshotEntity> findFirstByUserIdOrderBySnapshotDateDesc(String userId);

    @Override
    default Optional<PortfolioSnapshotEntity> findLatestBefore(String userId, LocalDate date) {
        return findFirstByUserIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(userId, date);
    }

    Optional<PortfolioSnapshotEntity> findFirstByUserIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(
            String userId, LocalDate date);
}

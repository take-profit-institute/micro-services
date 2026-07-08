package org.profit.candle.portfolio.analytics.repository;

import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotReader {
    List<PortfolioSnapshotEntity> findByUserIdAfterDate(String userId, LocalDate from);
    Optional<PortfolioSnapshotEntity> findByUserIdAndDate(String userId, LocalDate date);
    List<PortfolioSnapshotEntity> findDailySnapshotsAfterUserId(LocalDate date, String lastUserId, int limit);
    Optional<PortfolioSnapshotEntity> findLatestByUserId(String userId);
    // 직전 거래일 스냅샷 (당일 손익 계산용). 주말/휴장 갭을 고려해 date 이전 가장 최근 1건.
    Optional<PortfolioSnapshotEntity> findLatestBefore(String userId, LocalDate date);
}

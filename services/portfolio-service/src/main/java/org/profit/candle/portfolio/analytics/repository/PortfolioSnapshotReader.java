package org.profit.candle.portfolio.analytics.repository;

import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotReader {
    List<PortfolioSnapshotEntity> findByUserIdAfterDate(String userId, LocalDate from);
    Optional<PortfolioSnapshotEntity> findByUserIdAndDate(String userId, LocalDate date);
}

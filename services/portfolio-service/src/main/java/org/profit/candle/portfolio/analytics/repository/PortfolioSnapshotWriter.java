package org.profit.candle.portfolio.analytics.repository;

import org.profit.candle.portfolio.analytics.entity.PortfolioSnapshotEntity;

public interface PortfolioSnapshotWriter {
    PortfolioSnapshotEntity save(PortfolioSnapshotEntity entity);
}

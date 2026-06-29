package org.profit.candle.portfolio.analytics.service;

import org.profit.candle.portfolio.analytics.dto.PortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.RecordDailySnapshotCommand;

/**
 * 일별 스냅샷 쓰기(command). 읽기 통계는 {@link PortfolioAnalyticsService}가 담당한다.
 */
public interface PortfolioSnapshotService {
    PortfolioSnapshotResult recordDailySnapshot(RecordDailySnapshotCommand command);
}

package org.profit.candle.portfolio.analytics.service;

import org.profit.candle.portfolio.analytics.dto.PortfolioSnapshotResult;
import org.profit.candle.portfolio.analytics.dto.ListDailyPortfolioSnapshotsResult;
import org.profit.candle.portfolio.analytics.dto.RecordDailySnapshotCommand;

import java.time.LocalDate;

/** 일별 스냅샷 쓰기와 저장된 EOD 스냅샷 조회를 담당한다. */
public interface PortfolioSnapshotService {
    PortfolioSnapshotResult recordDailySnapshot(RecordDailySnapshotCommand command);
    ListDailyPortfolioSnapshotsResult listDailySnapshots(LocalDate snapshotDate, int pageSize, String pageToken);
}

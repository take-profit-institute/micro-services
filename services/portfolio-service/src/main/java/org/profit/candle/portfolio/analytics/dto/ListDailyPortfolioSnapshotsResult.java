package org.profit.candle.portfolio.analytics.dto;

import java.util.List;

public record ListDailyPortfolioSnapshotsResult(
        List<DailyPortfolioSnapshotResult> snapshots,
        String nextPageToken
) {}

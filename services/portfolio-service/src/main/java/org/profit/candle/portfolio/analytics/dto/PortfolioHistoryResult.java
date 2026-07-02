package org.profit.candle.portfolio.analytics.dto;

import java.util.List;

public record PortfolioHistoryResult(
        List<PortfolioSnapshotResult> snapshots,
        String periodReturnRate,
        long periodProfit
) {}

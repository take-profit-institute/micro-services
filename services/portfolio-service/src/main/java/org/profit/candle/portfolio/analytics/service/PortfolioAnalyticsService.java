package org.profit.candle.portfolio.analytics.service;

import org.profit.candle.portfolio.analytics.dto.PortfolioHistoryResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSummaryResult;
import org.profit.candle.portfolio.analytics.dto.SectorBreakdownResult;

import java.util.List;

public interface PortfolioAnalyticsService {
    PortfolioSummaryResult getSummary(String userId);
    PortfolioHistoryResult getHistory(String userId, int days);
    List<SectorBreakdownResult> getSectorBreakdown(String userId);
}

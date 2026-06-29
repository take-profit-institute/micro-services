package org.profit.candle.portfolio.analytics.service;

import org.profit.candle.portfolio.analytics.dto.MonthlyReturnResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioHistoryResult;
import org.profit.candle.portfolio.analytics.dto.PortfolioSummaryResult;
import org.profit.candle.portfolio.analytics.dto.SectorBreakdownResult;
import org.profit.candle.portfolio.analytics.dto.TradingStatsResult;

import java.util.List;

public interface PortfolioAnalyticsService {
    PortfolioSummaryResult getSummary(String userId);
    PortfolioHistoryResult getHistory(String userId, int days);
    List<SectorBreakdownResult> getSectorBreakdown(String userId);
    TradingStatsResult getTradingStats(String userId);
    List<MonthlyReturnResult> getMonthlyReturns(String userId, int months);
}

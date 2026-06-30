package org.profit.candle.portfolio.analytics.dto;

/**
 * 월별 수익률. 일별 스냅샷(portfolio_snapshots)을 월 단위로 집계한다.
 */
public record MonthlyReturnResult(
        String month,       // "2026-06" (YYYY-MM)
        String returnRate,  // 해당 월 수익률 "3.21" (%)
        long profit         // 해당 월 손익 (월말 총자산 − 월초 총자산)
) {}

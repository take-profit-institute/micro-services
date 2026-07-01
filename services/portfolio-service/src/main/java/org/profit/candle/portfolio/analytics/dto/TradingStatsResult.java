package org.profit.candle.portfolio.analytics.dto;

/**
 * 실현 거래 기반 투자 통계.
 * 승률은 본전(손익 0) 거래를 분모에서 제외한다.
 * 최대 수익/손실 종목은 종목별 누적 실현손익(HoldingEntity.realizedProfit) 기준이다.
 */
public record TradingStatsResult(
        String userId,
        int tradeCount,        // 청산된 거래 총 건수
        int winCount,          // 실현손익 > 0
        int lossCount,         // 실현손익 < 0
        String winRate,        // winCount / (winCount + lossCount) "62.50" (%)
        String avgHoldingDays, // 평균 보유기간(일) "5.30"
        String bestSymbol,     // 최대 수익 종목, 없으면 ""
        long bestProfit,
        String worstSymbol,    // 최대 손실 종목, 없으면 ""
        long worstProfit
) {}

package org.profit.candle.portfolio.holding.entity;

import java.time.Instant;

/**
 * 매도 체결 1건의 실현 결과. 거래 원장(realized trade) 적재에 사용한다.
 * openedAt 은 기능 도입 이전에 열린 포지션의 경우 null 일 수 있다.
 */
public record SellOutcome(
        long quantity,
        long entryPrice,
        long exitPrice,
        long realizedProfit,
        Instant openedAt,
        Instant closedAt
) {}

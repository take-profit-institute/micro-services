package org.profit.candle.ranking.ranking.event;

/** trading-service가 orderFilled topic으로 발행하는 현재 체결 payload. */
public record OrderFilledEvent(
        String orderId,
        String userId,
        String symbol,
        String side,
        long executedPriceKrw,
        long executedQuantity,
        long feeKrw,
        long taxKrw,
        long netAmountKrw) {}

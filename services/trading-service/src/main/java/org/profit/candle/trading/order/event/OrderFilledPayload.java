package org.profit.candle.trading.order.event;

public record OrderFilledPayload(
        String orderId, String userId, String symbol, String side,
        long executedPriceKrw, long executedQuantity,
        long feeKrw, long taxKrw, long netAmountKrw) {}

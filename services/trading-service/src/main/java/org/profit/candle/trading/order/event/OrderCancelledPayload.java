package org.profit.candle.trading.order.event;

public record OrderCancelledPayload(String orderId, String userId, long releasedAmount) {}
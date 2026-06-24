package org.profit.candle.trading.order.event;

public record OrderPlacedPayload(String orderId, String userId, String symbol, String side,
                                 long quantity, long price, long reservedAmount) {}
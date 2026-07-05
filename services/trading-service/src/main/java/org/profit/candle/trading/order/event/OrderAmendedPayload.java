package org.profit.candle.trading.order.event;

public record OrderAmendedPayload(String orderId, String originalOrderId, String userId,
                                  String symbol, long quantity, long priceKrw,
                                  long reservedAmountKrw) {}
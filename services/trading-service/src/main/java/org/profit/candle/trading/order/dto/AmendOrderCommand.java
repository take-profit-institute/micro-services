package org.profit.candle.trading.order.dto;

public record AmendOrderCommand(long quantity, long price, String idempotencyKey) {}

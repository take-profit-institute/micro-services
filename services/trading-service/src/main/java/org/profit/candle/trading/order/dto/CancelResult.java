package org.profit.candle.trading.order.dto;

import org.profit.candle.trading.order.entity.OrderEntity;

public record CancelResult(OrderEntity order, long releasedAmount) {}
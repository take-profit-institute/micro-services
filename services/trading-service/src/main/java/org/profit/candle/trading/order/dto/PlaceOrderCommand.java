package org.profit.candle.trading.order.dto;

import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;

public record PlaceOrderCommand(String symbol, OrderSideValue side, OrderKindValue kind,
                                long quantity, long price, String idempotencyKey) {}
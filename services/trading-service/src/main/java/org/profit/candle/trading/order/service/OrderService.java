package org.profit.candle.trading.order.service;

import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;

import java.util.UUID;

public interface OrderService {

    OrderEntity placeOrder(UUID userId, PlaceOrderCommand command);

    CancelResult cancelOrder(UUID userId, UUID orderId);

    CancelResult cancelExpiredPendingOrder(UUID orderId);
}
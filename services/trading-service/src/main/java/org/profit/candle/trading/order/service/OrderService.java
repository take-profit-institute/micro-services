package org.profit.candle.trading.order.service;

import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;

public interface OrderService {

    OrderEntity placeOrder(String actorId, PlaceOrderCommand command);

    CancelResult cancelOrder(String actorId, String orderId);
}
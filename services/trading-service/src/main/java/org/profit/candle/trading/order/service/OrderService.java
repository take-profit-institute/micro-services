package org.profit.candle.trading.order.service;

import org.profit.candle.trading.order.dto.AmendOrderCommand;
import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;

import java.util.UUID;

public interface OrderService {

    /**
     * 즉시 주문을 접수한다. (ORD-002/003)
     */
    OrderEntity placeOrder(UUID userId, PlaceOrderCommand command);

    /**
     * ReservationDue 이벤트 수신 시 Order 생성 (OPEN+LIMIT 예약 전환).
     * placeOrder()와 달리 거래시간 검증/lockBalance/즉시체결이 없다 —
     * 예약 생성 시점에 이미 처리됐기 때문이다.
     */
    OrderEntity placeOrderFromReservation(UUID userId, PlaceOrderCommand command);

    /**
     * 사용자가 직접 주문을 취소한다. (CAN-001~004)
     */
    CancelResult cancelOrder(UUID userId, UUID orderId);

    /**
     * 주문을 정정한다. (CAN-005~008)
     */
    OrderEntity amendOrder(UUID userId, UUID orderId, AmendOrderCommand command);

    /**
     * 배치(스케줄러)가 호출하는 만료 취소.
     */
    CancelResult cancelExpiredPendingOrder(UUID orderId);

    /**
     * batch-service의 ExpirePendingOrders gRPC가 호출하는 진입점.
     */
    int expirePendingOrders();
}
package org.profit.candle.trading.order.entity;

/** 도메인 주문 상태. proto OrderStatus와 grpc 계층에서 매핑한다. */
public enum OrderStatusValue {
    PENDING,
    FILLED,
    CANCELLED,
    REJECTED
}

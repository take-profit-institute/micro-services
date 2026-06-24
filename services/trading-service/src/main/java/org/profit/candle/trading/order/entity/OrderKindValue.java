package org.profit.candle.trading.order.entity;

/** 도메인 주문 유형. proto OrderKind와 grpc 계층에서 매핑한다. */
public enum OrderKindValue {
    MARKET,
    LIMIT,
    AFTER_HOURS_CLOSE
}

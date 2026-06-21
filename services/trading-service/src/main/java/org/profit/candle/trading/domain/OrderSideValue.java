package org.profit.candle.trading.domain;

/** 도메인 주문 방향. proto OrderSide와 grpc 계층에서 매핑한다. */
public enum OrderSideValue {
    BUY,
    SELL
}

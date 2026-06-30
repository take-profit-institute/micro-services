package org.profit.candle.trading.reservation.entity;

public enum ReservationTimingValue {
    OPEN,        // 당일 시가
    TODAY_CLOSE, // 당일 종가
    PREV_CLOSE   // 전일종가 (항상 익일 고정)
}

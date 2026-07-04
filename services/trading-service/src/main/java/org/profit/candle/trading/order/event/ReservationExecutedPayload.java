package org.profit.candle.trading.order.event;

/**
 * ReservationExecuted 이벤트 소비용 페이로드 (order 모듈).
 * reservation 모듈이 발행하는 {@code ReservationExecutedPayload}와 필드가 일치한다 —
 * 시장가/종가 예약이 확정 체결가로 실행됐을 때 수신해 실제 체결 Order를 만든다.
 */
public record ReservationExecutedPayload(
        String reservationId, String userId, String accountId,
        String symbol, String side, long quantity,
        long executedPrice, long reservedAmount) {}

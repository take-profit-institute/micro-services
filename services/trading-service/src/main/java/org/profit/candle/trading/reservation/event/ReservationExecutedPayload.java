package org.profit.candle.trading.reservation.event;

/**
 * ReservationExecuted 이벤트 페이로드.
 * PREV_CLOSE/TODAY_CLOSE 예약이 종가로 체결 완료됐을 때 발행한다.
 */
public record ReservationExecutedPayload(
        String reservationId, String userId, String accountId,
        String symbol, String side, long quantity,
        long executedPrice, long reservedAmount) {}
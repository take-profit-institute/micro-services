package org.profit.candle.trading.reservation.event;

/** ReservationCancelled 이벤트 페이로드. cancelReservation 성공 시 발행. */
public record ReservationCancelledPayload(String reservationId, String userId, long releasedAmount) {}

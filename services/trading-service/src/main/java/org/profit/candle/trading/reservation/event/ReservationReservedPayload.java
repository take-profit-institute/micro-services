package org.profit.candle.trading.reservation.event;

/** ReservationReserved 이벤트 페이로드. placeReservation 성공 시 발행. */
public record ReservationReservedPayload(
        String reservationId, String userId, String symbol, String side,
        String timing, String orderKind, long quantity, long price,
        long reservedAmount) {}
package org.profit.candle.trading.order.event;

/**
 * reservation_svc가 발행한 ReservationDue 이벤트 페이로드.
 * reservation.event.ReservationDuePayload와 동일한 구조 — 도메인 경계 의도적 중복.
 */
public record ReservationDuePayload(
        String reservationId,
        String userId,
        String accountId,
        String symbol,
        String side,
        long quantity,
        long priceKrw,
        long reservedAmountKrw,
        String idempotencyKey
) {}
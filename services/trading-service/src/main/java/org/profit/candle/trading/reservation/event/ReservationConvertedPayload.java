package org.profit.candle.trading.reservation.event;

/**
 * order_svc가 ReservationDue 처리 완료 후 발행하는 이벤트 페이로드.
 * reservation_svc의 {@link ReservationConvertedConsumer}가 수신해 markConverted()를 호출한다.
 */
public record ReservationConvertedPayload(
        String reservationId,
        String orderId,
        String userId
) {}
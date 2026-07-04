package org.profit.candle.trading.order.event;

/**
 * order_svc가 ReservationDue를 처리 완료 후 발행하는 Outbox 이벤트 페이로드.
 * reservation_svc 컨슈머가 수신해 markConverted()를 호출한다 (Option C: 전체 비동기 Kafka).
 */
public record ReservationConvertedPayload(
        String reservationId,
        String orderId,
        String userId
) {}
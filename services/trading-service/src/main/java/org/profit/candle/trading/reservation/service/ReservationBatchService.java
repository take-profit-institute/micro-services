package org.profit.candle.trading.reservation.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 배치 스케줄러가 gRPC로 호출하는 예약 체결 처리 서비스.
 * BFF가 직접 호출하지 않는다 — ReservationGrpcService의 배치 전용 RPC에서만 사용한다.
 */
public interface ReservationBatchService {

    /**
     * scheduled_date 도달한 OPEN+LIMIT RESERVED 예약을 CONVERTING으로 전이하고
     * ReservationDue 이벤트를 Outbox에 기록한다.
     * order_svc 컨슈머가 ReservationDue를 수신해 신규 Order를 생성하고
     * MarkReservationConverted RPC를 호출한다 (Option C: 전체 비동기 Kafka).
     *
     * @return 처리된 예약 건수
     */
    int processOpenLimitReservations(LocalDate targetDate);

    /**
     * order_svc가 ReservationDue를 처리 완료 후 호출 — CONVERTING → EXECUTED 전이,
     * converted_order_id 기록.
     */
    void markConverted(UUID reservationId, UUID convertedOrderId);
}

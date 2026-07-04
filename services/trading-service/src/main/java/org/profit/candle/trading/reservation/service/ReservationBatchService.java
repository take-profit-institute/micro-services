package org.profit.candle.trading.reservation.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 배치 스케줄러가 gRPC로 호출하는 예약 체결 처리 서비스.
 * BFF가 직접 호출하지 않는다 — ReservationGrpcService의 배치 전용 RPC에서만 사용한다.
 */
public interface ReservationBatchService {

    /**
     * 일별 배치 — scheduled_date 도달한 OPEN+LIMIT RESERVED 예약을 CONVERTING으로 전이하고
     * ReservationDue 이벤트를 Outbox에 기록한다.
     *
     * @return 처리된 예약 건수
     */
    int processOpenLimitReservations(LocalDate targetDate);

    /**
     * 건별 배치 목록 조회 — order의 findIdsByStatus 패턴과 동일.
     * 배치가 이 메서드로 대상 id 목록을 받아 processSingleOpenLimitReservation()을 건별 호출한다.
     */
    List<UUID> listOpenLimitReservationIds(LocalDate targetDate);

    /**
     * 건별 배치 처리 — order의 cancelExpiredPendingOrder 패턴과 동일.
     * RESERVED 상태 확인 → CONVERTING 전이 → ReservationDue Outbox 기록.
     *
     * @return 처리 성공 여부 (false면 이미 RESERVED 아님 — skip)
     */
    boolean processSingleOpenLimitReservation(UUID reservationId);

    /**
     * order_svc가 ReservationDue를 처리 완료 후 호출 — CONVERTING → EXECUTED 전이,
     * converted_order_id 기록.
     */
    void markConverted(UUID reservationId, UUID convertedOrderId);

    /**
     * OPEN+MARKET(시가 시장가) 예약 체결. Market Kafka 이벤트 수신 시 호출.
     *
     * @param targetDate 체결 대상 예약의 scheduled_date (보통 오늘)
     * @param symbol     현재가 이벤트의 종목코드
     * @param price      현재가 (원 단위)
     * @return 처리된 예약 건수
     */
    int processOpenMarketReservations(LocalDate targetDate, String symbol, long price);

    /**
     * PREV_CLOSE(전일종가) 예약 체결. 08:30 배치 트리거.
     *
     * @return 처리된 예약 건수
     */
    int processPrevCloseReservations(LocalDate targetDate);

    /**
     * TODAY_CLOSE(당일종가) 예약 체결. 15:40 배치 트리거.
     * ChartService.CloseDailyCandles로 종가 확정 후 배치가 호출해야 한다.
     *
     * @return 처리된 예약 건수
     */
    int processTodayCloseReservations(LocalDate targetDate);

    /**
     * CONVERTING 타임아웃 처리 대상 목록 조회.
     * scheduled_date가 targetDate이고 아직 CONVERTING 상태인 예약 id 목록을 반환한다.
     * 15:30 이후 (ExpirePendingOrders와 같은 시점) 배치가 호출한다.
     */
    List<UUID> listStaleConvertingReservationIds(LocalDate targetDate);

    /**
     * 건별 CONVERTING 타임아웃 처리.
     * CONVERTING → FAILED 전이 + reservedAmountKrw > 0이면 releaseBalance().
     *
     * @return 처리 성공 여부 (false면 이미 CONVERTING 아님)
     */
    boolean failStaleConvertingReservation(UUID reservationId);

    /**
     * EXPIRED 처리 대상 목록 조회 — scheduled_date가 targetDate이고 아직 RESERVED인 예약 id 목록.
     * 배치가 이 메서드로 목록을 받아 expireReservation()을 건별 호출한다.
     * 15:40 이후 (ProcessTodayCloseReservations 완료 후) 호출해야 한다.
     */
    List<UUID> listExpirableReservationIds(LocalDate targetDate);

    /**
     * 건별 EXPIRED 처리 — order의 cancelExpiredPendingOrder 패턴과 동일.
     * RESERVED → EXPIRED 전이 + reservedAmountKrw > 0이면 releaseBalance().
     *
     * @return 처리 성공 여부 (false면 이미 RESERVED 아님 — skip)
     */
    boolean expireReservation(UUID reservationId);
}
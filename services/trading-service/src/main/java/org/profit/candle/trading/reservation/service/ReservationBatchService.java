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

    /**
     * OPEN+MARKET(시가 시장가) 예약 체결. Market Kafka 이벤트 수신 시 호출.
     * 수신한 현재가로 당일 해당 종목의 OPEN+MARKET RESERVED 예약을 즉시 체결한다.
     *
     * @param targetDate 체결 대상 예약의 scheduled_date (보통 오늘)
     * @param symbol     현재가 이벤트의 종목코드
     * @param price      현재가 (원 단위)
     * @return 처리된 예약 건수
     */
    int processOpenMarketReservations(LocalDate targetDate, String symbol, long price);
     /* ChartService.GetPreviousClose(baseDate=오늘)로 전일 종가를 조회해 즉시 체결한다.
            *
            * @return 처리된 예약 건수
     */
    int processPrevCloseReservations(LocalDate targetDate);

    /**
     * TODAY_CLOSE(당일종가) 예약 체결. 15:40 배치 트리거.
     * ChartService.CloseDailyCandles로 종가 확정 후 배치가 호출해야 한다.
     * ChartService.GetPreviousClose(baseDate=내일)로 당일 종가를 조회해 즉시 체결한다.
     *
     * @return 처리된 예약 건수
     */
    int processTodayCloseReservations(LocalDate targetDate);
}
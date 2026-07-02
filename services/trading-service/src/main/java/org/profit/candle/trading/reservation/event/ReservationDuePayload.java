package org.profit.candle.trading.reservation.event;

/**
 * ReservationDue 이벤트 페이로드. 6종 중 "시가+지정가" 케이스만 이 경로를 탄다.
 * 배치가 scheduled_date 도달을 감지해 startConverting()을 호출한 직후 발행되며,
 * order_svc 컨슈머가 이를 수신해 신규 Order를 생성하고 ReservationConverted를 회신한다
 * (Option C: 전체 비동기 Kafka 전환, reservation→order 동기 호출 없음).
 */
public record ReservationDuePayload(String reservationId, String userId, String accountId, String symbol,
                                    String side, long quantity, long price, String idempotencyKey) {}

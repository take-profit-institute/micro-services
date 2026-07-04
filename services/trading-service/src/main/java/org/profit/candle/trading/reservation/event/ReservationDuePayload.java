package org.profit.candle.trading.reservation.event;

/**
 * ReservationDue 이벤트 페이로드. 6종 중 "시가+지정가" 케이스만 이 경로를 탄다.
 * reservedAmountKrw: 예약 생성 시점에 lockBalance된 금액 — order 생성 시 reserved_amount_krw로 사용.
 */
public record ReservationDuePayload(String reservationId, String userId, String accountId, String symbol,
                                    String side, long quantity, long priceKrw, long reservedAmountKrw,
                                    String idempotencyKey) {}
package org.profit.candle.trading.reservation.entity;

/**
 * 예약 주문 상태. DB ENUM({@code reservation.reservation_status})과 동일한 6종.
 *
 * <p>proto {@code ReservationStatus}는 현재 RESERVED/EXECUTED/CANCELLED 3종만
 * 정의되어 있다 — CONVERTING/FAILED/EXPIRED는 아직 proto에 없음. gRPC 매퍼에서
 * 누락분을 어떻게 노출할지는 별도 확인이 필요하다 (우선 도메인 enum은 DB 기준
 * 6종을 모두 유지하고, 매퍼에서 매핑 가능한 것만 변환).</p>
 */
public enum ReservationStatusValue {
    RESERVED,    // 접수 완료, 배치 실행 대기
    CONVERTING,  // 시가+지정가 전환 진행 중 (ReservationDue 발행~ReservationConverted 수신 전)
    EXECUTED,    // 체결 완료(자체 완결) 또는 전환 완료(시가+지정가)
    CANCELLED,   // 사용자 요청 취소
    FAILED,      // 잔고 부족 등 처리 실패
    EXPIRED      // 접수 마감 후 미처리 등 자동 만료
}

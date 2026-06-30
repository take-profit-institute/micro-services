package org.profit.candle.trading.reservation.exception;

import org.profit.candle.common.error.ErrorCode;

public enum ReservationErrorCode implements ErrorCode {


    INVALID_QUANTITY(
            "RESERVATION_INVALID_QUANTITY",
            "예약 수량은 1주 단위 양수여야 합니다."
    ),
    INVALID_PRICE(
            "RESERVATION_INVALID_PRICE",
            "지정가는 0보다 큰 정수여야 합니다."
    ),
    LIMIT_RESERVATION_REQUIRES_PRICE(
            "RESERVATION_LIMIT_REQUIRES_PRICE",
            "시가+지정가 예약은 가격을 지정해야 합니다."
    ),
    NON_LIMIT_RESERVATION_MUST_NOT_HAVE_PRICE(
            "RESERVATION_NON_LIMIT_MUST_NOT_HAVE_PRICE",
            "시가+지정가 외 예약은 가격을 지정할 수 없습니다."
    ),
    TIMING_ORDER_KIND_MISMATCH(
            "RESERVATION_TIMING_ORDER_KIND_MISMATCH",
            "시가 예약은 시장가/지정가만, 종가·전일종가 예약은 시간외종가만 허용됩니다."
    ),
    INVALID_SCHEDULED_DATE(
            "RESERVATION_INVALID_SCHEDULED_DATE",
            "예약 실행일은 내일부터 7일 이내여야 합니다 (전일종가는 내일로 고정)."
    ),
    DUPLICATE_PENDING_RESERVATION(
            "RESERVATION_DUPLICATE_PENDING",
            "해당 종목에 이미 접수된 예약이 있습니다."
    ),
    RESERVATION_NOT_RESERVED(
            "RESERVATION_NOT_RESERVED",
            "RESERVED 상태인 예약만 처리할 수 있습니다."
    ),
    RESERVATION_NOT_CONVERTING(
            "RESERVATION_NOT_CONVERTING",
            "전환 진행 중(CONVERTING) 상태인 예약만 전환 완료 처리할 수 있습니다."
    ),
    NOT_CONVERTIBLE(
            "RESERVATION_NOT_CONVERTIBLE",
            "시가+지정가 예약만 order_svc로 전환할 수 있습니다."
    ),
    BATCH_DEADLINE_PASSED(
            "RESERVATION_BATCH_DEADLINE_PASSED",
            "배치 마감 후에는 취소/정정할 수 없습니다."
    ),
    RESERVATION_NOT_FOUND(
            "RESERVATION_NOT_FOUND",
            "예약을 찾을 수 없습니다."
    );

    private final String code;
    private final String message;

    ReservationErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}

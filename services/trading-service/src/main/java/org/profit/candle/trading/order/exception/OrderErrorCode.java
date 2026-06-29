package org.profit.candle.trading.order.exception;

import org.profit.candle.common.error.ErrorCode;

public enum OrderErrorCode implements ErrorCode {

    INVALID_QUANTITY(
            "ORDER_INVALID_QUANTITY",
            "주문 수량은 1주 단위 양수여야 합니다."
    ),
    INVALID_PRICE(
            "ORDER_INVALID_PRICE",
            "지정가는 0보다 큰 정수여야 합니다."
    ),
    LIMIT_ORDER_REQUIRES_PRICE(
            "ORDER_LIMIT_REQUIRES_PRICE",
            "지정가 주문은 가격을 지정해야 합니다."
    ),
    MARKET_ORDER_MUST_NOT_HAVE_PRICE(
            "ORDER_MARKET_MUST_NOT_HAVE_PRICE",
            "시장가 주문은 가격을 지정할 수 없습니다."
    ),
    DUPLICATE_PENDING_ORDER(
            "ORDER_DUPLICATE_PENDING",
            "해당 종목에 이미 대기 중인 주문이 있습니다."
    ),
    ORDER_NOT_PENDING(
            "ORDER_NOT_PENDING",
            "대기 중인 주문만 처리할 수 있습니다."
    ),
    MARKET_ORDER_CANNOT_BE_CANCELLED(
            "ORDER_MARKET_CANNOT_BE_CANCELLED",
            "시장가 주문은 취소할 수 없습니다."
    ),
    ORDER_NOT_FOUND(
            "ORDER_NOT_FOUND",
            "주문을 찾을 수 없습니다."
    );

    private final String code;
    private final String message;

    OrderErrorCode(String code, String message) {
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

package org.profit.candle.portfolio.holding.exception;

import org.profit.candle.common.error.ErrorCode;

public enum HoldingErrorCode implements ErrorCode {
    HOLDING_NOT_FOUND("HOLDING-001", "보유 종목을 찾을 수 없습니다");

    private final String code;
    private final String message;

    HoldingErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
}

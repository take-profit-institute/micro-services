package org.profit.candle.batch.portfolio.eod.exception;

import org.profit.candle.common.error.ErrorCode;

public enum EodBatchErrorCode implements ErrorCode {
    CASH_BALANCE_INVALID("BATCH_EOD_CASH_BALANCE_INVALID", "현금 잔고가 올바르지 않습니다."),
    SEED_CAPITAL_INVALID("BATCH_EOD_SEED_CAPITAL_INVALID", "초기 원금이 올바르지 않습니다."),
    HOLDING_QUANTITY_INVALID("BATCH_EOD_HOLDING_QUANTITY_INVALID", "보유 수량이 올바르지 않습니다."),
    CLOSING_PRICE_INVALID("BATCH_EOD_CLOSING_PRICE_INVALID", "종가가 없거나 올바르지 않습니다."),
    QUOTE_DATE_MISMATCH("BATCH_EOD_QUOTE_DATE_MISMATCH", "시세 기준일이 거래일과 다릅니다."),
    TRADING_BALANCE_MISSING("BATCH_EOD_TRADING_BALANCE_MISSING", "거래 잔고 응답이 없습니다."),
    EXTERNAL_CLIENT_FAILED("BATCH_EOD_EXTERNAL_CLIENT_FAILED", "외부 서비스 호출에 실패했습니다."),
    EXTERNAL_CLIENT_RETRYABLE(
            "BATCH_EOD_EXTERNAL_CLIENT_RETRYABLE",
            "재시도 가능한 외부 서비스 오류가 발생했습니다.",
            true
    );

    private final String code;
    private final String message;
    private final boolean retryable;

    EodBatchErrorCode(String code, String message) {
        this(code, message, false);
    }

    EodBatchErrorCode(String code, String message, boolean retryable) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    public boolean retryable() {
        return retryable;
    }
}

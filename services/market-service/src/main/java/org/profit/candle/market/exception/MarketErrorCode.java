package org.profit.candle.market.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.profit.candle.common.error.ErrorCode;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum MarketErrorCode implements ErrorCode {
    KIWOOM_HTTP_FAILED("MARKET_KIWOOM_HTTP_FAILED", "시세 정보를 불러올 수 없습니다"),
    KIWOOM_BUSINESS_FAILED("MARKET_KIWOOM_BUSINESS_FAILED", "시세 정보를 불러올 수 없습니다"),
    KIWOOM_INVALID_RESPONSE("MARKET_KIWOOM_INVALID_RESPONSE", "시세 정보를 불러올 수 없습니다");

    private final String code;
    private final String message;
}

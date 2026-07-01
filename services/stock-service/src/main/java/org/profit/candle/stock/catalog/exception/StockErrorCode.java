package org.profit.candle.stock.catalog.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.profit.candle.common.error.ErrorCode;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum StockErrorCode implements ErrorCode {
    STOCK_NOT_FOUND("STOCK_NOT_FOUND", "종목을 찾을 수 없습니다");

    private final String code;
    private final String message;
}

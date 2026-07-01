package org.profit.candle.stock.chart.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.profit.candle.common.error.ErrorCode;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ChartErrorCode implements ErrorCode {
    INVALID_CANDLE_REQUEST("INVALID_CANDLE_REQUEST", "캔들 조회 요청이 올바르지 않습니다"),
    CHART_DATA_UNAVAILABLE("CHART_DATA_UNAVAILABLE", "차트 데이터를 불러올 수 없습니다");

    private final String code;
    private final String message;
}

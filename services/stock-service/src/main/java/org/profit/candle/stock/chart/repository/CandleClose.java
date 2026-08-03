package org.profit.candle.stock.chart.repository;

/** {@code findClosesAt} 결과 행. 마감 이벤트 발행에 필요한 종목코드/종가만 담는 경량 projection. */
public record CandleClose(String stockCode, long close) {
}

package org.profit.candle.stock.chart.repository;

/** {@code findPriceStats} 결과 행. window 내 최고/최저가만 담는 경량 projection.
 * 해당 종목의 일봉이 없으면 MAX/MIN 이 NULL 이므로 nullable 로 받는다. */
public interface PriceStatsRow {
    Long getHigh();

    Long getLow();
}

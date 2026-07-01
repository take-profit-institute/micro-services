package org.profit.candle.stock.chart.service;

import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.dto.CandleResult;
import org.profit.candle.stock.chart.dto.SparklineResult;

import java.time.Instant;
import java.util.List;

public interface ChartService {
    List<CandleResult> getCandles(String code, CandleInterval interval, int limit, Instant to);

    List<SparklineResult> getSparklines(List<String> codes, CandleInterval interval, int points);

    /** 기준일자 직전(exclusive)의 마지막 일봉 종가. 없으면 백필 후에도 없으면 예외. */
    CandleResult getPreviousClose(String code, Instant date);
}

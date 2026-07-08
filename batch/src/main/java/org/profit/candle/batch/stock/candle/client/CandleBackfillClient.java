package org.profit.candle.batch.stock.candle.client;

/** stock-service ChartService.BackfillCandles 호출 — DAY_1 최근 count개 일봉을 적재한다. */
public interface CandleBackfillClient {

    /** @return upsert된 캔들 수(키움 미설정/무응답이면 0). */
    int backfillDaily(String code, int count);
}

package org.profit.candle.batch.stock.candle.client;

import java.time.Instant;
import java.util.List;

/** stock-service ChartService.BackfillCandles 호출 — DAY_1 최근 count개 일봉을 적재한다. */
public interface CandleBackfillClient {

    /** @return upsert된 캔들 수(키움 미설정/무응답이면 0). */
    int backfillDaily(String code, int count);

    /** 요청 종목 중 해당 일봉이 이미 stock-service DB에 있는 종목 코드. */
    List<String> findExistingDailyCodes(List<String> codes, Instant openTime);
}

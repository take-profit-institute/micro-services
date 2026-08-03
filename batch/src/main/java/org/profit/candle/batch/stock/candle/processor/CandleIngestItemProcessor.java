package org.profit.candle.batch.stock.candle.processor;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.stock.candle.client.CandleBackfillClient;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;
import org.profit.candle.batch.stock.candle.model.CandleIngestResult;
import org.profit.candle.batch.stock.candle.policy.StockCandleRetryExecutor;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/** 종목코드마다 stock-service에 DAY_1 백필을 요청한다(실 적재/upsert는 서버가 수행). */
@Slf4j
public class CandleIngestItemProcessor implements ItemProcessor<String, CandleIngestResult> {

    private final CandleBackfillClient backfillClient;
    private final StockCandleRetryExecutor retryExecutor;
    private final int candleCount;

    public CandleIngestItemProcessor(
            CandleBackfillClient backfillClient,
            StockCandleRetryExecutor retryExecutor,
            int candleCount
    ) {
        this.backfillClient = backfillClient;
        this.retryExecutor = retryExecutor;
        this.candleCount = candleCount;
    }

    @Override
    public CandleIngestResult process(String code) {
        try {
            int upserted = retryExecutor.execute(() -> backfillClient.backfillDaily(code, candleCount));
            return CandleIngestResult.succeeded(code, upserted);
        } catch (StockCandleException exception) {
            // 여기서 던지면 faultTolerant 가 청크를 롤백하고 아이템을 1건씩 재실행(scan)하는데,
            // process 안에 키움 HTTP 호출이 있어 실패 1건마다 청크 크기만큼의 호출이 통째로 반복된다.
            // 종목 단위 실패는 결과 값으로 흘려보내고, 누적 상한 판정은 writer 가 맡는다.
            log.warn("[Stock Candle Ingest] backfill failed code={} reason={}", code, exception.toString());
            return CandleIngestResult.failed(code, exception.toString());
        }
    }
}

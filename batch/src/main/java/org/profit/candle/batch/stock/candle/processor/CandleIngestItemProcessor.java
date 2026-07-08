package org.profit.candle.batch.stock.candle.processor;

import org.profit.candle.batch.stock.candle.client.CandleBackfillClient;
import org.profit.candle.batch.stock.candle.model.CandleIngestResult;
import org.profit.candle.batch.stock.candle.policy.StockCandleRetryExecutor;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/** 종목코드마다 stock-service에 DAY_1 백필을 요청한다(실 적재/upsert는 서버가 수행). */
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
        int upserted = retryExecutor.execute(() -> backfillClient.backfillDaily(code, candleCount));
        return new CandleIngestResult(code, upserted);
    }
}

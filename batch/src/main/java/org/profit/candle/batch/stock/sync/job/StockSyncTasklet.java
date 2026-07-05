package org.profit.candle.batch.stock.sync.job;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.stock.sync.client.StockSyncClient;
import org.profit.candle.batch.stock.sync.exception.StockSyncException;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockSyncTasklet implements Tasklet {

    private static final int MAX_ATTEMPTS = 3;

    private final StockSyncClient stockSyncClient;

    public StockSyncTasklet(StockSyncClient stockSyncClient) {
        this.stockSyncClient = stockSyncClient;
    }

    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {
        sync(StockSyncClient.Market.KOSPI);
        sync(StockSyncClient.Market.KOSDAQ);
        return RepeatStatus.FINISHED;
    }

    private void sync(StockSyncClient.Market market) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                StockSyncClient.Result result = stockSyncClient.sync(market);
                log.info(
                        "[Stock Sync] market={}, upserted={}, total={}, attempt={}",
                        market,
                        result.upserted(),
                        result.total(),
                        attempt
                );
                return;
            } catch (StockSyncException exception) {
                if (!exception.retryable() || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
                log.warn(
                        "[Stock Sync] Retry external call. market={}, attempt={}",
                        market,
                        attempt
                );
            }
        }
    }
}

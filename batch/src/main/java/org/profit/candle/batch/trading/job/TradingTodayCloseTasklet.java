package org.profit.candle.batch.trading.job;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.trading.client.DailyCandleCloseClient;
import org.profit.candle.batch.trading.client.TradingBatchClient;
import org.profit.candle.batch.trading.policy.TradingBatchRetryExecutor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@Slf4j
public class TradingTodayCloseTasklet implements Tasklet {

    private static final String IDEMPOTENCY_KEY_PREFIX = "stock-close-daily:";

    private final LocalDate businessDate;
    private final DailyCandleCloseClient candleCloseClient;
    private final TradingBatchClient tradingBatchClient;
    private final TradingBatchRetryExecutor retryExecutor;

    public TradingTodayCloseTasklet(
            LocalDate businessDate,
            DailyCandleCloseClient candleCloseClient,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        this.businessDate = businessDate;
        this.candleCloseClient = candleCloseClient;
        this.tradingBatchClient = tradingBatchClient;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {
        String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + businessDate;
        int closedCount = retryExecutor.execute(
                () -> candleCloseClient.close(businessDate, idempotencyKey)
        );
        int processedCount = retryExecutor.execute(
                () -> tradingBatchClient.processTodayCloseReservations(businessDate)
        );
        log.info(
                "[Trading Today Close] businessDate={}, closedCandles={}, processedReservations={}",
                businessDate,
                closedCount,
                processedCount
        );
        return RepeatStatus.FINISHED;
    }
}

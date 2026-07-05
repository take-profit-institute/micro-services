package org.profit.candle.batch.trading.job;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.trading.client.TradingBatchClient;
import org.profit.candle.batch.trading.policy.TradingBatchRetryExecutor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/** 장 마감 시점에 남은 PENDING 지정가 주문을 일괄 취소한다. */
@Slf4j
public class TradingExpirePendingOrdersTasklet implements Tasklet {

    private final TradingBatchClient tradingBatchClient;
    private final TradingBatchRetryExecutor retryExecutor;

    public TradingExpirePendingOrdersTasklet(
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        this.tradingBatchClient = tradingBatchClient;
        this.retryExecutor = retryExecutor;
    }

    /** 미체결 주문 만료 RPC를 호출하고 취소 건수를 기록한다. */
    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {
        int cancelledCount = retryExecutor.execute(tradingBatchClient::expirePendingOrders);
        log.info("[Trading Pending Order Expiry] cancelled={}", cancelledCount);
        return RepeatStatus.FINISHED;
    }
}

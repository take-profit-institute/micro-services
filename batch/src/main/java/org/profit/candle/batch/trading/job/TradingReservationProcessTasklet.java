package org.profit.candle.batch.trading.job;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.trading.client.TradingBatchClient;
import org.profit.candle.batch.trading.policy.TradingBatchRetryExecutor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/** 거래일 기준 예약 일괄 처리 RPC를 실행하는 오전 Job 공통 Tasklet이다. */
@Slf4j
public class TradingReservationProcessTasklet implements Tasklet {

    private final LocalDate businessDate;
    private final Operation operation;
    private final TradingBatchClient tradingBatchClient;
    private final TradingBatchRetryExecutor retryExecutor;

    public TradingReservationProcessTasklet(
            LocalDate businessDate,
            Operation operation,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        this.businessDate = businessDate;
        this.operation = operation;
        this.tradingBatchClient = tradingBatchClient;
        this.retryExecutor = retryExecutor;
    }

    /** 선택된 예약 처리 RPC를 호출하고 처리 건수를 기록한다. */
    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {
        int processedCount = retryExecutor.execute(this::process);
        log.info(
                "[Trading Reservation Process] operation={}, businessDate={}, processed={}",
                operation,
                businessDate,
                processedCount
        );
        return RepeatStatus.FINISHED;
    }

    /** Job 종류에 맞는 Trading 일괄 처리 RPC를 선택한다. */
    private int process() {
        return switch (operation) {
            case PREVIOUS_CLOSE -> tradingBatchClient
                    .processPreviousCloseReservations(businessDate);
            case OPEN_LIMIT -> tradingBatchClient
                    .processOpenLimitReservations(businessDate);
        };
    }

    public enum Operation {
        PREVIOUS_CLOSE,
        OPEN_LIMIT
    }
}

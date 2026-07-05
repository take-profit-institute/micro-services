package org.profit.candle.batch.trading.job;

import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.trading.client.TradingBatchClient;
import org.profit.candle.batch.trading.policy.TradingBatchRetryExecutor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/** ReservationDue 후 완료되지 못한 CONVERTING 예약을 건별 FAILED 처리한다. */
@Slf4j
public class TradingFailStaleConvertingTasklet implements Tasklet {

    private final LocalDate businessDate;
    private final TradingBatchClient tradingBatchClient;
    private final TradingBatchRetryExecutor retryExecutor;

    public TradingFailStaleConvertingTasklet(
            LocalDate businessDate,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        this.businessDate = businessDate;
        this.tradingBatchClient = tradingBatchClient;
        this.retryExecutor = retryExecutor;
    }

    /** stale 대상 목록을 조회하고 각 예약을 독립적으로 실패 처리한다. */
    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {
        List<String> reservationIds = retryExecutor.execute(
                () -> tradingBatchClient.listStaleConvertingReservations(businessDate)
        );
        int failedCount = 0;
        int skippedCount = 0;

        for (String reservationId : reservationIds) {
            if (fail(reservationId)) {
                failedCount++;
            } else {
                skippedCount++;
            }
        }

        log.info(
                "[Trading Stale Converting] businessDate={}, targets={}, failed={}, skipped={}",
                businessDate,
                reservationIds.size(),
                failedCount,
                skippedCount
        );
        return RepeatStatus.FINISHED;
    }

    /** 이미 다른 상태인 예약은 건너뛰고 일시적 오류만 최대 3회 재시도한다. */
    private boolean fail(String reservationId) {
        return retryExecutor.execute(
                () -> tradingBatchClient.failStaleConvertingReservation(reservationId)
        );
    }
}

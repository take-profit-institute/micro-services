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

/** 당일 처리가 끝난 뒤에도 RESERVED로 남은 예약을 건별 EXPIRED 처리한다. */
@Slf4j
public class TradingExpireReservationsTasklet implements Tasklet {

    private final LocalDate businessDate;
    private final TradingBatchClient tradingBatchClient;
    private final TradingBatchRetryExecutor retryExecutor;

    public TradingExpireReservationsTasklet(
            LocalDate businessDate,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        this.businessDate = businessDate;
        this.tradingBatchClient = tradingBatchClient;
        this.retryExecutor = retryExecutor;
    }

    /** 만료 대상 목록을 조회하고 각 예약을 독립적으로 만료 처리한다. */
    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {
        List<String> reservationIds = retryExecutor.execute(
                () -> tradingBatchClient.listExpirableReservations(businessDate)
        );
        int expiredCount = 0;
        int skippedCount = 0;

        for (String reservationId : reservationIds) {
            if (expire(reservationId)) {
                expiredCount++;
            } else {
                skippedCount++;
            }
        }

        log.info(
                "[Trading Reservation Expiry] businessDate={}, targets={}, expired={}, skipped={}",
                businessDate,
                reservationIds.size(),
                expiredCount,
                skippedCount
        );
        return RepeatStatus.FINISHED;
    }

    /** 이미 다른 상태인 예약은 false로 건너뛰고 일시적 오류만 최대 3회 재시도한다. */
    private boolean expire(String reservationId) {
        return retryExecutor.execute(
                () -> tradingBatchClient.expireReservation(reservationId)
        );
    }
}

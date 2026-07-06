package org.profit.candle.batch.ranking.job;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.ranking.client.RankingBatchClient;
import org.profit.candle.batch.ranking.exception.RankingBatchErrorCode;
import org.profit.candle.batch.ranking.exception.RankingBatchException;
import org.profit.candle.batch.ranking.idempotency.RankingIdempotencyKeyFactory;
import org.profit.candle.batch.ranking.policy.RankingBatchRetryExecutor;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/** Portfolio EOD 완료 후 같은 거래일의 일별 랭킹을 확정한다. */
@Slf4j
public class RankingFinalizeTasklet implements Tasklet {

    private final LocalDate rankingDate;
    private final PortfolioEodCompletionGuard eodCompletionGuard;
    private final RankingBatchClient rankingBatchClient;
    private final RankingIdempotencyKeyFactory keyFactory;
    private final RankingBatchRetryExecutor retryExecutor;

    public RankingFinalizeTasklet(
            LocalDate rankingDate,
            PortfolioEodCompletionGuard eodCompletionGuard,
            RankingBatchClient rankingBatchClient,
            RankingIdempotencyKeyFactory keyFactory,
            RankingBatchRetryExecutor retryExecutor
    ) {
        this.rankingDate = rankingDate;
        this.eodCompletionGuard = eodCompletionGuard;
        this.rankingBatchClient = rankingBatchClient;
        this.keyFactory = keyFactory;
        this.retryExecutor = retryExecutor;
    }

    /** 선행 EOD 완료를 확인하고 Ranking Service에 확정 명령을 보낸다. */
    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {
        if (!eodCompletionGuard.completed(rankingDate)) {
            throw new RankingBatchException(
                    RankingBatchErrorCode.PORTFOLIO_EOD_NOT_COMPLETED
            );
        }

        String idempotencyKey = keyFactory.create(rankingDate);
        RankingBatchClient.Result result = retryExecutor.execute(
                () -> rankingBatchClient.finalizeDailyRanking(rankingDate, idempotencyKey)
        );
        log.info(
                "[Daily Ranking] finalized. rankingDate={}, rankedUserCount={}",
                result.rankingDate(),
                result.rankedUserCount()
        );
        return RepeatStatus.FINISHED;
    }
}

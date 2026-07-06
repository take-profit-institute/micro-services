package org.profit.candle.batch.ranking.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.ranking.client.RankingBatchClient;
import org.profit.candle.batch.ranking.exception.RankingBatchErrorCode;
import org.profit.candle.batch.ranking.exception.RankingBatchException;
import org.profit.candle.batch.ranking.idempotency.RankingIdempotencyKeyFactory;
import org.profit.candle.batch.ranking.policy.RankingBatchRetryExecutor;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class RankingFinalizeTaskletTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    /** EOD가 완료된 거래일은 결정적 키로 Ranking Service를 한 번 호출한다. */
    @Test
    void finalizesRankingAfterPortfolioEodCompletion() {
        PortfolioEodCompletionGuard guard = completedGuard(true);
        RankingBatchClient client = mock(RankingBatchClient.class);
        RankingIdempotencyKeyFactory keyFactory = new RankingIdempotencyKeyFactory();
        String key = keyFactory.create(DATE);
        when(client.finalizeDailyRanking(DATE, key))
                .thenReturn(new RankingBatchClient.Result(DATE, 42));
        RankingFinalizeTasklet tasklet = tasklet(guard, client, keyFactory);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(client).finalizeDailyRanking(DATE, key);
    }

    /** EOD가 완료되지 않았으면 Ranking RPC를 호출하지 않고 즉시 실패한다. */
    @Test
    void failsWithoutCallingRankingWhenPortfolioEodIsIncomplete() {
        PortfolioEodCompletionGuard guard = completedGuard(false);
        RankingBatchClient client = mock(RankingBatchClient.class);
        RankingFinalizeTasklet tasklet = tasklet(
                guard,
                client,
                new RankingIdempotencyKeyFactory()
        );

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(RankingBatchException.class)
                .satisfies(exception -> assertThat(
                        ((RankingBatchException) exception).errorCode()
                ).isEqualTo(RankingBatchErrorCode.PORTFOLIO_EOD_NOT_COMPLETED));
        verify(client, never()).finalizeDailyRanking(eq(DATE), anyString());
    }

    /** 일시적 gRPC 오류는 같은 요청으로 최대 세 번 시도한다. */
    @Test
    void retriesRetryableFailureThreeTimes() {
        PortfolioEodCompletionGuard guard = completedGuard(true);
        RankingBatchClient client = mock(RankingBatchClient.class);
        RankingIdempotencyKeyFactory keyFactory = new RankingIdempotencyKeyFactory();
        String key = keyFactory.create(DATE);
        when(client.finalizeDailyRanking(DATE, key))
                .thenThrow(retryableFailure());
        RankingFinalizeTasklet tasklet = tasklet(guard, client, keyFactory);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(RankingBatchException.class);
        verify(client, times(3)).finalizeDailyRanking(DATE, key);
    }

    /** 테스트 대상 Tasklet을 실제 키 생성·재시도 정책과 함께 만든다. */
    private RankingFinalizeTasklet tasklet(
            PortfolioEodCompletionGuard guard,
            RankingBatchClient client,
            RankingIdempotencyKeyFactory keyFactory
    ) {
        return new RankingFinalizeTasklet(
                DATE,
                guard,
                client,
                keyFactory,
                new RankingBatchRetryExecutor()
        );
    }

    /** EOD 완료 여부를 고정해서 반환하는 검증 대상을 만든다. */
    private PortfolioEodCompletionGuard completedGuard(boolean completed) {
        PortfolioEodCompletionGuard guard = mock(PortfolioEodCompletionGuard.class);
        when(guard.completed(DATE)).thenReturn(completed);
        return guard;
    }

    /** 재시도 가능한 Ranking 외부 오류를 생성한다. */
    private RankingBatchException retryableFailure() {
        return new RankingBatchException(
                RankingBatchErrorCode.EXTERNAL_CLIENT_RETRYABLE,
                new IllegalStateException("temporary failure")
        );
    }
}

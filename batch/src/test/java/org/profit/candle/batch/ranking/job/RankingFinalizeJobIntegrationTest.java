package org.profit.candle.batch.ranking.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.client.SeedCapitalProvider;
import org.profit.candle.batch.portfolio.eod.client.SnapshotTargetClient;
import org.profit.candle.batch.ranking.client.RankingBatchClient;
import org.profit.candle.batch.ranking.exception.RankingBatchErrorCode;
import org.profit.candle.batch.ranking.exception.RankingBatchException;
import org.profit.candle.batch.ranking.idempotency.RankingIdempotencyKeyFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ranking-batch;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.grpc.server.port=0",
        "spring.batch.job.enabled=false",
        "batch.schedule.smoke.enabled=false",
        "batch.schedule.portfolio-eod.enabled=false",
        "batch.schedule.stock-sync.enabled=false",
        "batch.schedule.trading.enabled=false",
        "batch.ranking.enabled=false"
})
class RankingFinalizeJobIntegrationTest {

    private static final LocalDate COMPLETED_DATE = LocalDate.of(2026, 7, 6);
    private static final LocalDate RESTART_DATE = LocalDate.of(2026, 7, 7);
    private static final LocalDate INCOMPLETE_EOD_DATE = LocalDate.of(2026, 7, 8);

    private final JobOperator jobOperator;
    private final Job rankingJob;
    private final RankingIdempotencyKeyFactory keyFactory;

    @MockitoBean
    private PortfolioEodCompletionGuard eodCompletionGuard;

    @MockitoBean
    private RankingBatchClient rankingBatchClient;

    @MockitoBean
    private SnapshotTargetClient snapshotTargetClient;

    @MockitoBean
    private SeedCapitalProvider seedCapitalProvider;

    @Autowired
    RankingFinalizeJobIntegrationTest(
            JobOperator jobOperator,
            @Qualifier(RankingFinalizeJobConfiguration.JOB_NAME) Job rankingJob,
            RankingIdempotencyKeyFactory keyFactory
    ) {
        this.jobOperator = jobOperator;
        this.rankingJob = rankingJob;
        this.keyFactory = keyFactory;
    }

    /** 완료된 같은 날짜 JobInstance가 다시 실행되지 않는지 검증한다. */
    @Test
    void blocksDuplicateExecutionAfterCompletion() throws Exception {
        String key = keyFactory.create(COMPLETED_DATE);
        when(eodCompletionGuard.completed(COMPLETED_DATE)).thenReturn(true);
        when(rankingBatchClient.finalizeDailyRanking(COMPLETED_DATE, key))
                .thenReturn(new RankingBatchClient.Result(COMPLETED_DATE, 42));
        JobParameters parameters = parameters(COMPLETED_DATE);

        JobExecution execution = jobOperator.start(rankingJob, parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThatThrownBy(() -> jobOperator.start(rankingJob, parameters))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
        verify(rankingBatchClient).finalizeDailyRanking(COMPLETED_DATE, key);
    }

    /** 실패한 같은 날짜 JobInstance가 동일 멱등성 키로 재시작되는지 검증한다. */
    @Test
    void restartsFailedExecutionWithSameIdempotencyKey() throws Exception {
        String key = keyFactory.create(RESTART_DATE);
        when(eodCompletionGuard.completed(RESTART_DATE)).thenReturn(true);
        when(rankingBatchClient.finalizeDailyRanking(RESTART_DATE, key))
                .thenThrow(new RankingBatchException(RankingBatchErrorCode.EXTERNAL_CLIENT_FAILED))
                .thenReturn(new RankingBatchClient.Result(RESTART_DATE, 20));
        JobParameters parameters = parameters(RESTART_DATE);

        JobExecution failed = jobOperator.start(rankingJob, parameters);
        JobExecution restarted = jobOperator.start(rankingJob, parameters);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(rankingBatchClient, times(2))
                .finalizeDailyRanking(RESTART_DATE, key);
    }

    /** EOD 미완료 Job은 실패하고 Ranking Service를 호출하지 않는지 검증한다. */
    @Test
    void failsWithoutRpcWhenPortfolioEodIsIncomplete() throws Exception {
        when(eodCompletionGuard.completed(INCOMPLETE_EOD_DATE)).thenReturn(false);

        JobExecution execution = jobOperator.start(
                rankingJob,
                parameters(INCOMPLETE_EOD_DATE)
        );

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        verify(rankingBatchClient, never())
                .finalizeDailyRanking(eq(INCOMPLETE_EOD_DATE), anyString());
    }

    /** jobName과 rankingDate만 식별값으로 사용하는 실행 파라미터를 생성한다. */
    private JobParameters parameters(LocalDate rankingDate) {
        return new JobParametersBuilder()
                .addString("jobName", RankingFinalizeJobConfiguration.JOB_NAME)
                .addString("rankingDate", rankingDate.toString())
                .toJobParameters();
    }
}

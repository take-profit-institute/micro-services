package org.profit.candle.batch.ranking.job;

import java.time.LocalDate;
import org.profit.candle.batch.ranking.client.RankingBatchClient;
import org.profit.candle.batch.ranking.idempotency.RankingIdempotencyKeyFactory;
import org.profit.candle.batch.ranking.policy.RankingBatchRetryExecutor;
import org.profit.candle.batch.support.listener.BatchLoggingListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class RankingFinalizeJobConfiguration {

    public static final String JOB_NAME = "dailyRankingFinalizeJob";
    public static final String STEP_NAME = "dailyRankingFinalizeStep";

    /** 일별 랭킹 확정 Step 하나로 구성된 Job을 등록한다. */
    @Bean(name = JOB_NAME)
    public Job dailyRankingFinalizeJob(
            JobRepository jobRepository,
            @Qualifier(STEP_NAME) Step step,
            BatchLoggingListener loggingListener
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(loggingListener)
                .start(step)
                .build();
    }

    /** 랭킹 확정 Tasklet을 Batch 트랜잭션 경계에서 실행한다. */
    @Bean(name = STEP_NAME)
    public Step dailyRankingFinalizeStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("dailyRankingFinalizeTasklet") Tasklet tasklet
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(tasklet)
                .transactionManager(transactionManager)
                .build();
    }

    /** Job parameter의 KST 거래일로 실행별 Tasklet을 생성한다. */
    @Bean
    @StepScope
    public Tasklet dailyRankingFinalizeTasklet(
            @Value("#{jobParameters['rankingDate']}") String rankingDate,
            PortfolioEodCompletionGuard eodCompletionGuard,
            RankingBatchClient rankingBatchClient,
            RankingIdempotencyKeyFactory keyFactory,
            RankingBatchRetryExecutor retryExecutor
    ) {
        return new RankingFinalizeTasklet(
                LocalDate.parse(rankingDate),
                eodCompletionGuard,
                rankingBatchClient,
                keyFactory,
                retryExecutor
        );
    }
}

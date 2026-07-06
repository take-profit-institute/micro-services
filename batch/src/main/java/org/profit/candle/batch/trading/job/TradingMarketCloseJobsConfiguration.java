package org.profit.candle.batch.trading.job;

import java.time.LocalDate;
import org.profit.candle.batch.support.listener.BatchLoggingListener;
import org.profit.candle.batch.trading.client.TradingBatchClient;
import org.profit.candle.batch.trading.policy.TradingBatchRetryExecutor;
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
public class TradingMarketCloseJobsConfiguration {

    public static final String EXPIRE_PENDING_JOB_NAME = "tradingExpirePendingOrdersJob";
    public static final String EXPIRE_PENDING_STEP_NAME = "tradingExpirePendingOrdersStep";
    public static final String FAIL_STALE_JOB_NAME = "tradingFailStaleConvertingJob";
    public static final String FAIL_STALE_STEP_NAME = "tradingFailStaleConvertingStep";

    /** 15:30 미체결 주문 만료 Job을 등록한다. */
    @Bean(name = EXPIRE_PENDING_JOB_NAME)
    public Job tradingExpirePendingOrdersJob(
            JobRepository jobRepository,
            @Qualifier(EXPIRE_PENDING_STEP_NAME) Step step,
            BatchLoggingListener loggingListener
    ) {
        return buildJob(EXPIRE_PENDING_JOB_NAME, step, jobRepository, loggingListener);
    }

    /** 15:30 stale CONVERTING 정리 Job을 등록한다. */
    @Bean(name = FAIL_STALE_JOB_NAME)
    public Job tradingFailStaleConvertingJob(
            JobRepository jobRepository,
            @Qualifier(FAIL_STALE_STEP_NAME) Step step,
            BatchLoggingListener loggingListener
    ) {
        return buildJob(FAIL_STALE_JOB_NAME, step, jobRepository, loggingListener);
    }

    /** 미체결 주문 만료 Tasklet을 실행하는 Step을 등록한다. */
    @Bean(name = EXPIRE_PENDING_STEP_NAME)
    public Step tradingExpirePendingOrdersStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("tradingExpirePendingOrdersTasklet") Tasklet tasklet
    ) {
        return buildStep(
                EXPIRE_PENDING_STEP_NAME,
                tasklet,
                jobRepository,
                transactionManager
        );
    }

    /** stale CONVERTING 정리 Tasklet을 실행하는 Step을 등록한다. */
    @Bean(name = FAIL_STALE_STEP_NAME)
    public Step tradingFailStaleConvertingStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("tradingFailStaleConvertingTasklet") Tasklet tasklet
    ) {
        return buildStep(FAIL_STALE_STEP_NAME, tasklet, jobRepository, transactionManager);
    }

    /** 미체결 주문 만료 Tasklet을 생성한다. */
    @Bean
    @StepScope
    public Tasklet tradingExpirePendingOrdersTasklet(
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        return new TradingExpirePendingOrdersTasklet(tradingBatchClient, retryExecutor);
    }

    /** Job parameter의 거래일로 stale CONVERTING 정리 Tasklet을 생성한다. */
    @Bean
    @StepScope
    public Tasklet tradingFailStaleConvertingTasklet(
            @Value("#{jobParameters['businessDate']}") String businessDate,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        return new TradingFailStaleConvertingTasklet(
                LocalDate.parse(businessDate),
                tradingBatchClient,
                retryExecutor
        );
    }

    /** 공통 Job 생성 규칙을 적용한다. */
    private Job buildJob(
            String name,
            Step step,
            JobRepository jobRepository,
            BatchLoggingListener loggingListener
    ) {
        return new JobBuilder(name, jobRepository)
                .listener(loggingListener)
                .start(step)
                .build();
    }

    /** 공통 Tasklet Step 생성 규칙을 적용한다. */
    private Step buildStep(
            String name,
            Tasklet tasklet,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder(name, jobRepository)
                .tasklet(tasklet)
                .transactionManager(transactionManager)
                .build();
    }
}

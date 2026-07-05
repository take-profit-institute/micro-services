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
public class TradingMorningJobsConfiguration {

    public static final String PREVIOUS_CLOSE_JOB_NAME = "tradingPreviousCloseJob";
    public static final String PREVIOUS_CLOSE_STEP_NAME = "tradingPreviousCloseStep";
    public static final String OPEN_LIMIT_JOB_NAME = "tradingOpenLimitJob";
    public static final String OPEN_LIMIT_STEP_NAME = "tradingOpenLimitStep";

    /** 08:30 전일 종가 예약 체결 Job을 등록한다. */
    @Bean(name = PREVIOUS_CLOSE_JOB_NAME)
    public Job tradingPreviousCloseJob(
            JobRepository jobRepository,
            @Qualifier(PREVIOUS_CLOSE_STEP_NAME) Step step,
            BatchLoggingListener loggingListener
    ) {
        return buildJob(PREVIOUS_CLOSE_JOB_NAME, step, jobRepository, loggingListener);
    }

    /** 09:00 OPEN+LIMIT 예약 전환 Job을 등록한다. */
    @Bean(name = OPEN_LIMIT_JOB_NAME)
    public Job tradingOpenLimitJob(
            JobRepository jobRepository,
            @Qualifier(OPEN_LIMIT_STEP_NAME) Step step,
            BatchLoggingListener loggingListener
    ) {
        return buildJob(OPEN_LIMIT_JOB_NAME, step, jobRepository, loggingListener);
    }

    /** 08:30 전일 종가 처리 Step을 등록한다. */
    @Bean(name = PREVIOUS_CLOSE_STEP_NAME)
    public Step tradingPreviousCloseStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("tradingPreviousCloseTasklet") Tasklet tasklet
    ) {
        return buildStep(
                PREVIOUS_CLOSE_STEP_NAME,
                tasklet,
                jobRepository,
                transactionManager
        );
    }

    /** 09:00 OPEN+LIMIT 처리 Step을 등록한다. */
    @Bean(name = OPEN_LIMIT_STEP_NAME)
    public Step tradingOpenLimitStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("tradingOpenLimitTasklet") Tasklet tasklet
    ) {
        return buildStep(OPEN_LIMIT_STEP_NAME, tasklet, jobRepository, transactionManager);
    }

    /** Job parameter의 거래일로 전일 종가 예약 Tasklet을 생성한다. */
    @Bean
    @StepScope
    public Tasklet tradingPreviousCloseTasklet(
            @Value("#{jobParameters['businessDate']}") String businessDate,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        return tasklet(
                businessDate,
                TradingReservationProcessTasklet.Operation.PREVIOUS_CLOSE,
                tradingBatchClient,
                retryExecutor
        );
    }

    /** Job parameter의 거래일로 OPEN+LIMIT 예약 Tasklet을 생성한다. */
    @Bean
    @StepScope
    public Tasklet tradingOpenLimitTasklet(
            @Value("#{jobParameters['businessDate']}") String businessDate,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        return tasklet(
                businessDate,
                TradingReservationProcessTasklet.Operation.OPEN_LIMIT,
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

    /** 거래일과 작업 종류를 적용한 공통 Tasklet을 생성한다. */
    private Tasklet tasklet(
            String businessDate,
            TradingReservationProcessTasklet.Operation operation,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        return new TradingReservationProcessTasklet(
                LocalDate.parse(businessDate),
                operation,
                tradingBatchClient,
                retryExecutor
        );
    }
}

package org.profit.candle.batch.trading.job;

import java.time.LocalDate;
import org.profit.candle.batch.support.listener.BatchLoggingListener;
import org.profit.candle.batch.trading.client.DailyCandleCloseClient;
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
public class TradingTodayCloseJobConfiguration {

    public static final String JOB_NAME = "tradingTodayCloseJob";
    public static final String STEP_NAME = "tradingTodayCloseStep";

    @Bean(name = JOB_NAME)
    public Job tradingTodayCloseJob(
            JobRepository jobRepository,
            @Qualifier(STEP_NAME) Step step,
            BatchLoggingListener loggingListener
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(loggingListener)
                .start(step)
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step tradingTodayCloseStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("tradingTodayCloseTasklet") Tasklet tasklet
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(tasklet)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet tradingTodayCloseTasklet(
            @Value("#{jobParameters['businessDate']}") String businessDate,
            DailyCandleCloseClient candleCloseClient,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        return new TradingTodayCloseTasklet(
                LocalDate.parse(businessDate),
                candleCloseClient,
                tradingBatchClient,
                retryExecutor
        );
    }
}

package org.profit.candle.batch.stock.sync.job;

import org.profit.candle.batch.support.listener.BatchLoggingListener;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class StockSyncJobConfiguration {

    public static final String JOB_NAME = "stockSyncJob";
    public static final String STEP_NAME = "stockSyncStep";

    @Bean(name = JOB_NAME)
    public Job stockSyncJob(
            JobRepository jobRepository,
            @Qualifier(STEP_NAME) Step stockSyncStep,
            BatchLoggingListener loggingListener
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(loggingListener)
                .start(stockSyncStep)
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step stockSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            StockSyncTasklet tasklet
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(tasklet)
                .transactionManager(transactionManager)
                .build();
    }
}

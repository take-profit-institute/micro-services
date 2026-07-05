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
public class TradingExpireReservationsJobConfiguration {

    public static final String JOB_NAME = "tradingExpireReservationsJob";
    public static final String STEP_NAME = "tradingExpireReservationsStep";

    /** 예약 만료 Step 하나로 구성된 Spring Batch Job을 등록한다. */
    @Bean(name = JOB_NAME)
    public Job tradingExpireReservationsJob(
            JobRepository jobRepository,
            @Qualifier(STEP_NAME) Step step,
            BatchLoggingListener loggingListener
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(loggingListener)
                .start(step)
                .build();
    }

    /** 건별 만료 Tasklet을 실행하는 트랜잭션 Step을 등록한다. */
    @Bean(name = STEP_NAME)
    public Step tradingExpireReservationsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("tradingExpireReservationsTasklet") Tasklet tasklet
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(tasklet)
                .transactionManager(transactionManager)
                .build();
    }

    /** Job parameter의 거래일을 사용해 실행별 Tasklet을 생성한다. */
    @Bean
    @StepScope
    public Tasklet tradingExpireReservationsTasklet(
            @Value("#{jobParameters['businessDate']}") String businessDate,
            TradingBatchClient tradingBatchClient,
            TradingBatchRetryExecutor retryExecutor
    ) {
        return new TradingExpireReservationsTasklet(
                LocalDate.parse(businessDate),
                tradingBatchClient,
                retryExecutor
        );
    }
}

package org.profit.candle.batch.portfolio.eod.job;

import java.time.LocalDate;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.client.CashBalanceClient;
import org.profit.candle.batch.portfolio.eod.client.ClosingPriceClient;
import org.profit.candle.batch.portfolio.eod.client.PortfolioSnapshotClient;
import org.profit.candle.batch.portfolio.eod.client.SeedCapitalProvider;
import org.profit.candle.batch.portfolio.eod.client.SnapshotTargetClient;
import org.profit.candle.batch.portfolio.eod.idempotency.SnapshotIdempotencyKeyFactory;
import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.profit.candle.batch.portfolio.eod.policy.EodRetryExecutor;
import org.profit.candle.batch.portfolio.eod.policy.SnapshotCalculator;
import org.profit.candle.batch.portfolio.eod.processor.SnapshotItemProcessor;
import org.profit.candle.batch.portfolio.eod.reader.SnapshotTargetItemReader;
import org.profit.candle.batch.portfolio.eod.repository.ClosingPriceStageRepository;
import org.profit.candle.batch.portfolio.eod.writer.SnapshotItemWriter;
import org.profit.candle.batch.support.listener.BatchLoggingListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(
        name = "batch.schedule.portfolio-eod.enabled",
        havingValue = "true"
)
public class PortfolioEodJobConfiguration {

    public static final String JOB_NAME = "portfolioEodSnapshotJob";
    public static final String PRICE_STEP_NAME = "portfolioEodClosingPriceStep";
    public static final String SNAPSHOT_STEP_NAME = "portfolioEodSnapshotStep";

    @Bean(name = JOB_NAME)
    public Job portfolioEodSnapshotJob(
            JobRepository jobRepository,
            @Qualifier(PRICE_STEP_NAME) Step priceStep,
            @Qualifier(SNAPSHOT_STEP_NAME) Step snapshotStep,
            BatchLoggingListener loggingListener
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(loggingListener)
                .start(priceStep)
                .next(snapshotStep)
                .build();
    }

    @Bean(name = PRICE_STEP_NAME)
    public Step closingPriceStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("portfolioEodClosingPriceTasklet") Tasklet tasklet
    ) {
        return new StepBuilder(PRICE_STEP_NAME, jobRepository)
                .tasklet(tasklet)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean(name = SNAPSHOT_STEP_NAME)
    public Step snapshotStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            BatchProperties batchProperties,
            @Qualifier("portfolioEodItemReader") ItemReader<SnapshotTarget> reader,
            @Qualifier("portfolioEodItemProcessor")
            ItemProcessor<SnapshotTarget, SnapshotCommand> processor,
            @Qualifier("portfolioEodItemWriter") ItemWriter<SnapshotCommand> writer
    ) {
        return new StepBuilder(SNAPSHOT_STEP_NAME, jobRepository)
                .<SnapshotTarget, SnapshotCommand>chunk(
                        batchProperties.schedule().portfolioEod().chunkSize()
                )
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet portfolioEodClosingPriceTasklet(
            @Value("#{stepExecution.jobExecution.jobInstanceId}") Long jobInstanceId,
            @Value("#{jobParameters['businessDate']}") String businessDate,
            BatchProperties batchProperties,
            SnapshotTargetClient targetClient,
            ClosingPriceClient closingPriceClient,
            ClosingPriceStageRepository priceRepository,
            EodRetryExecutor retryExecutor
    ) {
        return new ClosingPriceStageTasklet(
                jobInstanceId,
                LocalDate.parse(businessDate),
                batchProperties.schedule().portfolioEod().symbolBatchSize(),
                targetClient,
                closingPriceClient,
                priceRepository,
                retryExecutor
        );
    }

    @Bean
    @StepScope
    public SnapshotTargetItemReader portfolioEodItemReader(
            @Value("#{jobParameters['businessDate']}") String businessDate,
            BatchProperties batchProperties,
            SnapshotTargetClient targetClient,
            EodRetryExecutor retryExecutor
    ) {
        return new SnapshotTargetItemReader(
                targetClient,
                retryExecutor,
                LocalDate.parse(businessDate),
                batchProperties.schedule().portfolioEod().chunkSize()
        );
    }

    @Bean
    @StepScope
    public SnapshotItemProcessor portfolioEodItemProcessor(
            @Value("#{stepExecution.jobExecution.jobInstanceId}") Long jobInstanceId,
            @Value("#{jobParameters['businessDate']}") String businessDate,
            CashBalanceClient cashBalanceClient,
            SeedCapitalProvider seedCapitalProvider,
            ClosingPriceStageRepository priceRepository,
            SnapshotCalculator calculator,
            SnapshotIdempotencyKeyFactory keyFactory,
            EodRetryExecutor retryExecutor
    ) {
        return new SnapshotItemProcessor(
                jobInstanceId,
                LocalDate.parse(businessDate),
                cashBalanceClient,
                seedCapitalProvider,
                priceRepository,
                calculator,
                keyFactory,
                retryExecutor
        );
    }

    @Bean
    public SnapshotItemWriter portfolioEodItemWriter(
            PortfolioSnapshotClient snapshotClient
    ) {
        return new SnapshotItemWriter(snapshotClient);
    }
}

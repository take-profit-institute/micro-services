package org.profit.candle.batch.stock.candle.job;

import org.profit.candle.batch.stock.candle.client.CandleBackfillClient;
import org.profit.candle.batch.stock.candle.client.StockCatalogClient;
import org.profit.candle.batch.stock.candle.model.CandleIngestResult;
import org.profit.candle.batch.stock.candle.policy.StockCandleRetryExecutor;
import org.profit.candle.batch.stock.candle.processor.CandleIngestItemProcessor;
import org.profit.candle.batch.stock.candle.reader.StockCatalogItemReader;
import org.profit.candle.batch.stock.candle.writer.CandleIngestItemWriter;
import org.profit.candle.batch.support.listener.BatchLoggingListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 일봉 캔들 적재 잡 — LISTED 종목을 chunk로 순회하며 stock-service ChartService.BackfillCandles를
 * 호출해 그날 일봉을 적재한다. 마감 확정(TradingTodayClose)/EOD 스냅샷이 종가를 읽을 수 있게 선행한다.
 */
@Configuration
public class StockCandleIngestJobConfiguration {

    public static final String JOB_NAME = "stockCandleIngestJob";
    public static final String STEP_NAME = "stockCandleIngestStep";
    private static final String READER = "stockCandleItemReader";
    private static final String PROCESSOR = "stockCandleItemProcessor";
    private static final String WRITER = "stockCandleItemWriter";

    @Bean(name = JOB_NAME)
    public Job stockCandleIngestJob(
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
    public Step stockCandleIngestStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Value("${batch.schedule.stock-candle.chunk-size:50}") int chunkSize,
            @Qualifier(READER) ItemReader<String> reader,
            @Qualifier(PROCESSOR) ItemProcessor<String, CandleIngestResult> processor,
            @Qualifier(WRITER) ItemWriter<CandleIngestResult> writer
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<String, CandleIngestResult>chunk(chunkSize)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean(name = READER)
    @StepScope
    public StockCatalogItemReader stockCandleItemReader(
            @Value("${batch.schedule.stock-candle.page-size:100}") int pageSize,
            StockCatalogClient catalogClient,
            StockCandleRetryExecutor retryExecutor
    ) {
        return new StockCatalogItemReader(catalogClient, retryExecutor, pageSize);
    }

    @Bean(name = PROCESSOR)
    @StepScope
    public CandleIngestItemProcessor stockCandleItemProcessor(
            @Value("${batch.schedule.stock-candle.candle-count:10}") int candleCount,
            CandleBackfillClient backfillClient,
            StockCandleRetryExecutor retryExecutor
    ) {
        return new CandleIngestItemProcessor(backfillClient, retryExecutor, candleCount);
    }

    @Bean(name = WRITER)
    public CandleIngestItemWriter stockCandleItemWriter() {
        return new CandleIngestItemWriter();
    }
}

package org.profit.candle.batch.stock.candle.job;

import org.profit.candle.batch.stock.candle.client.CandleBackfillClient;
import org.profit.candle.batch.stock.candle.client.StockCatalogClient;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;
import org.profit.candle.batch.stock.candle.listener.CandleIngestSkipListener;
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
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
    private static final String STEP_EXECUTOR = "stockCandleStepTaskExecutor";
    private static final String SKIP_LISTENER = "stockCandleSkipListener";

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
            @Value("${batch.schedule.stock-candle.skip-limit:200}") int skipLimit,
            @Qualifier(READER) ItemReader<String> reader,
            @Qualifier(PROCESSOR) ItemProcessor<String, CandleIngestResult> processor,
            @Qualifier(WRITER) ItemWriter<CandleIngestResult> writer,
            @Qualifier(STEP_EXECUTOR) AsyncTaskExecutor stepTaskExecutor,
            CandleIngestSkipListener skipListener
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<String, CandleIngestResult>chunk(chunkSize)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                // (a) 멀티스레드 스텝 — 워커 스레드마다 자기 청크를 돌린다(동시성=풀 크기).
                // 아이템 단위 병렬이 아니라 청크 단위 병렬이며, read()는 워커들이 동시에 호출하므로
                // 리더가 내부에서 동기화한다. 커서 재시작은 불가라 리더가 상태를 저장하지 않는다
                // (재실행 시 이미 적재된 종목은 리더의 filterMissingDailyCandles가 걸러낸다).
                .taskExecutor(stepTaskExecutor)
                // (c) 종목 단위 백필 실패는 processor가 예외 대신 실패 결과로 흘려보내고 writer가 상한을 판정한다
                // (예외로 던지면 청크 롤백 후 1건씩 재실행되며 청크 전체의 키움 호출이 통째로 반복된다).
                // 여기 skip은 카탈로그 페이지 read 실패 전용으로만 남는다 — read skip은 청크 스캔을 유발하지 않는다.
                .faultTolerant()
                .skip(StockCandleException.class)
                .skipLimit(skipLimit)
                .skipListener(skipListener)
                .build();
    }

    @Bean(name = READER)
    @StepScope
    public StockCatalogItemReader stockCandleItemReader(
            @Value("${batch.schedule.stock-candle.page-size:100}") int pageSize,
            @Value("#{jobParameters['businessDate']}") String businessDate,
            @Value("${batch.schedule.zone-id:Asia/Seoul}") String zoneId,
            StockCatalogClient catalogClient,
            CandleBackfillClient candleClient,
            StockCandleRetryExecutor retryExecutor
    ) {
        return new StockCatalogItemReader(catalogClient, candleClient, retryExecutor, pageSize, businessDate, zoneId);
    }

    /**
     * 청크 내 종목 처리를 병렬화하는 풀. 한 청크(chunkSize개)의 process 태스크를 한꺼번에 submit하므로
     * 큐는 넉넉히(unbounded) 두고 스레드 수(=core=max)로 실제 동시성을 concurrency로 제한한다.
     */
    @Bean(name = STEP_EXECUTOR)
    public AsyncTaskExecutor stockCandleStepTaskExecutor(
            @Value("${batch.schedule.stock-candle.concurrency:8}") int concurrency
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("stock-candle-");
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean(name = SKIP_LISTENER)
    public CandleIngestSkipListener candleIngestSkipListener() {
        return new CandleIngestSkipListener();
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

    /** 실패 카운터를 들고 있어 스텝 스코프로 둔다 — 실행마다 0에서 다시 센다. */
    @Bean(name = WRITER)
    @StepScope
    public CandleIngestItemWriter stockCandleItemWriter(
            @Value("${batch.schedule.stock-candle.failure-limit:200}") int failureLimit
    ) {
        return new CandleIngestItemWriter(failureLimit);
    }
}

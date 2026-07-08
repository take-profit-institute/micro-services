package org.profit.candle.batch.stock.candle.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.client.SeedCapitalProvider;
import org.profit.candle.batch.portfolio.eod.client.SnapshotTargetClient;
import org.profit.candle.batch.stock.candle.client.CandleBackfillClient;
import org.profit.candle.batch.stock.candle.client.StockCatalogClient;
import org.profit.candle.batch.stock.candle.exception.StockCandleErrorCode;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;
import org.profit.candle.batch.support.parameter.JobParameterFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 캔들 적재 잡의 (a) 청크 내 병렬 처리와 (c) 실패 종목 skip을 end-to-end로 검증한다.
 * JobOperator는 테스트에서 동기지만(=BatchTestExecutorConfiguration), step 내부 executor는 여전히
 * 병렬로 돌기 때문에 동시성이 관측된다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-candle;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.grpc.server.port=0",
        "spring.batch.job.enabled=false",
        "batch.schedule.smoke.enabled=false",
        "batch.schedule.portfolio-eod.enabled=false",
        "batch.schedule.stock-sync.enabled=false",
        "batch.schedule.trading.enabled=false",
        "batch.ranking.enabled=false",
        "batch.schedule.stock-candle.chunk-size=10",
        "batch.schedule.stock-candle.concurrency=4",
        "batch.schedule.stock-candle.skip-limit=50"
})
class StockCandleIngestJobIntegrationTest {

    private static final int TOTAL = 15;
    private static final Set<String> FAILING = Set.of("000012", "000013", "000014");

    private final JobOperator jobOperator;
    private final Job stockCandleIngestJob;
    private final JobParameterFactory parameterFactory;

    @MockitoBean
    private StockCatalogClient catalogClient;

    @MockitoBean
    private CandleBackfillClient backfillClient;

    @MockitoBean
    private SnapshotTargetClient snapshotTargetClient;

    @MockitoBean
    private SeedCapitalProvider seedCapitalProvider;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();

    @Autowired
    StockCandleIngestJobIntegrationTest(
            JobOperator jobOperator,
            @Qualifier(StockCandleIngestJobConfiguration.JOB_NAME) Job stockCandleIngestJob,
            JobParameterFactory parameterFactory
    ) {
        this.jobOperator = jobOperator;
        this.stockCandleIngestJob = stockCandleIngestJob;
        this.parameterFactory = parameterFactory;
    }

    @Test
    void backfillsListedStocksInParallelAndSkipsFailures() throws Exception {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < TOTAL; i++) {
            codes.add(String.format("%06d", i));
        }
        when(catalogClient.listListedCodes(eq(0), anyInt()))
                .thenReturn(new StockCatalogClient.Page(codes, 1));

        when(backfillClient.backfillDaily(anyString(), anyInt())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            int current = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(60);
                if (FAILING.contains(code)) {
                    throw new StockCandleException(StockCandleErrorCode.EXTERNAL_CLIENT_FAILED, null);
                }
                return 5;
            } finally {
                inFlight.decrementAndGet();
            }
        });

        JobExecution execution = jobOperator.start(
                stockCandleIngestJob,
                parameterFactory.createRunParameters(StockCandleIngestJobConfiguration.JOB_NAME)
        );

        // 잡은 실패 종목이 있어도 완료된다.
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        StepExecution step = execution.getStepExecutions().iterator().next();
        // (c) 실패한 3종목은 skip으로 흡수된다.
        assertThat(step.getSkipCount()).isEqualTo(FAILING.size());
        // 성공 종목은 write까지 도달한다.
        assertThat(step.getWriteCount()).isEqualTo(TOTAL - FAILING.size());
        // (a) 청크 내 백필이 실제로 동시에 실행됐다(동기라면 최대 1).
        assertThat(maxInFlight.get()).isGreaterThan(1);
    }
}

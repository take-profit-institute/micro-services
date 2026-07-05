package org.profit.candle.batch.stock.sync.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.profit.candle.batch.portfolio.eod.client.SeedCapitalProvider;
import org.profit.candle.batch.portfolio.eod.client.SnapshotTargetClient;
import org.profit.candle.batch.stock.sync.client.StockSyncClient;
import org.profit.candle.batch.support.parameter.JobParameterFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-sync;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.batch.job.enabled=false",
        "batch.schedule.smoke.enabled=false",
        "batch.schedule.portfolio-eod.enabled=false",
        "batch.schedule.stock-sync.enabled=false"
})
class StockSyncJobIntegrationTest {

    private final JobOperator jobOperator;
    private final Job stockSyncJob;
    private final JobParameterFactory parameterFactory;

    @MockitoBean
    private StockSyncClient stockSyncClient;

    @MockitoBean
    private SnapshotTargetClient snapshotTargetClient;

    @MockitoBean
    private SeedCapitalProvider seedCapitalProvider;

    @Autowired
    StockSyncJobIntegrationTest(
            JobOperator jobOperator,
            @Qualifier(StockSyncJobConfiguration.JOB_NAME) Job stockSyncJob,
            JobParameterFactory parameterFactory
    ) {
        this.jobOperator = jobOperator;
        this.stockSyncJob = stockSyncJob;
        this.parameterFactory = parameterFactory;
    }

    @Test
    void completesJobAfterSyncingBothMarketsInOrder() throws Exception {
        when(stockSyncClient.sync(StockSyncClient.Market.KOSPI))
                .thenReturn(new StockSyncClient.Result(1_900, 2_000));
        when(stockSyncClient.sync(StockSyncClient.Market.KOSDAQ))
                .thenReturn(new StockSyncClient.Result(1_600, 1_700));

        JobExecution execution = jobOperator.start(
                stockSyncJob,
                parameterFactory.createRunParameters(StockSyncJobConfiguration.JOB_NAME)
        );

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        InOrder order = inOrder(stockSyncClient);
        order.verify(stockSyncClient).sync(StockSyncClient.Market.KOSPI);
        order.verify(stockSyncClient).sync(StockSyncClient.Market.KOSDAQ);
    }
}

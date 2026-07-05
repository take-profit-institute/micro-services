package org.profit.candle.batch.stock.sync.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.support.parameter.JobParameterFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;

class StockSyncJobSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-05T07:30:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Test
    void doesNotStartJobWhenDisabled() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        Job job = mock(Job.class);
        StockSyncJobScheduler scheduler = scheduler(operator, job, false);

        scheduler.runStockSyncJob();

        verify(operator, never()).start(any(Job.class), any());
    }

    @Test
    void startsJobWhenEnabled() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        Job job = mock(Job.class);
        when(operator.start(any(Job.class), any())).thenReturn(mock(JobExecution.class));
        StockSyncJobScheduler scheduler = scheduler(operator, job, true);

        scheduler.runStockSyncJob();

        verify(operator).start(any(Job.class), any());
    }

    private StockSyncJobScheduler scheduler(
            JobOperator operator,
            Job job,
            boolean enabled
    ) {
        return new StockSyncJobScheduler(
                operator,
                job,
                properties(enabled),
                new JobParameterFactory(CLOCK)
        );
    }

    private BatchProperties properties(boolean enabled) {
        return new BatchProperties(
                new BatchProperties.Schedule(
                        "Asia/Seoul",
                        new BatchProperties.Smoke(false, "0 0 * * * *"),
                        new BatchProperties.PortfolioEod(
                                false,
                                "0 0 16 * * MON-FRI",
                                100,
                                500
                        ),
                        new BatchProperties.StockSync(
                                enabled,
                                "0 30 16 * * MON-FRI"
                        ),
                        new BatchProperties.Trading(false, "", "", "", "")
                ),
                new BatchProperties.Grpc(
                        "market",
                        "stock",
                        "trading",
                        "portfolio",
                        300,
                        1_000,
                        120_000,
                        120_000
                )
        );
    }
}

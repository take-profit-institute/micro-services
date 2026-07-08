package org.profit.candle.batch.stock.candle.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.stock.candle.job.StockCandleIngestJobConfiguration;
import org.profit.candle.batch.support.parameter.JobParameterFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "batch.schedule.stock-candle.enabled",
        havingValue = "true"
)
public class StockCandleIngestJobScheduler {

    private final JobOperator jobOperator;
    private final Job stockCandleIngestJob;
    private final JobParameterFactory parameterFactory;

    public StockCandleIngestJobScheduler(
            JobOperator jobOperator,
            @Qualifier(StockCandleIngestJobConfiguration.JOB_NAME) Job stockCandleIngestJob,
            JobParameterFactory parameterFactory
    ) {
        this.jobOperator = jobOperator;
        this.stockCandleIngestJob = stockCandleIngestJob;
        this.parameterFactory = parameterFactory;
    }

    @Scheduled(
            cron = "${batch.schedule.stock-candle.cron}",
            zone = "${batch.schedule.zone-id}"
    )
    public void runStockCandleIngestJob() {
        try {
            JobParameters parameters = parameterFactory.createRunParameters(
                    StockCandleIngestJobConfiguration.JOB_NAME
            );
            JobExecution execution = jobOperator.start(stockCandleIngestJob, parameters);
            log.info(
                    "[Stock Candle Ingest] Job execution requested. status={}",
                    execution.getStatus()
            );
        } catch (Exception exception) {
            log.error("[Stock Candle Ingest] Failed to execute job.", exception);
        }
    }
}

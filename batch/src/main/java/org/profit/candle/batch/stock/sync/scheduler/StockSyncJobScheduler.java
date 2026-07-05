package org.profit.candle.batch.stock.sync.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.stock.sync.job.StockSyncJobConfiguration;
import org.profit.candle.batch.support.parameter.JobParameterFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockSyncJobScheduler {

    private final JobOperator jobOperator;
    private final Job stockSyncJob;
    private final BatchProperties batchProperties;
    private final JobParameterFactory parameterFactory;

    public StockSyncJobScheduler(
            JobOperator jobOperator,
            @Qualifier(StockSyncJobConfiguration.JOB_NAME) Job stockSyncJob,
            BatchProperties batchProperties,
            JobParameterFactory parameterFactory
    ) {
        this.jobOperator = jobOperator;
        this.stockSyncJob = stockSyncJob;
        this.batchProperties = batchProperties;
        this.parameterFactory = parameterFactory;
    }

    @Scheduled(
            cron = "${batch.schedule.stock-sync.cron}",
            zone = "${batch.schedule.zone-id}"
    )
    public void runStockSyncJob() {
        if (!batchProperties.schedule().stockSync().enabled()) {
            return;
        }

        try {
            JobParameters parameters = parameterFactory.createRunParameters(
                    StockSyncJobConfiguration.JOB_NAME
            );
            JobExecution execution = jobOperator.start(stockSyncJob, parameters);
            log.info(
                    "[Stock Sync] Job execution requested. status={}",
                    execution.getStatus()
            );
        } catch (Exception exception) {
            log.error("[Stock Sync] Failed to execute job.", exception);
        }
    }
}

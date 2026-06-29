package org.profit.candle.batch.smoke.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.smoke.job.BatchSmokeJobConfiguration;
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
public class BatchSmokeJobScheduler {

    private final JobOperator jobOperator;
    private final Job batchSmokeJob;
    private final BatchProperties batchProperties;
    private final JobParameterFactory jobParameterFactory;

    public BatchSmokeJobScheduler(
            JobOperator jobOperator,
            @Qualifier(BatchSmokeJobConfiguration.JOB_NAME) Job batchSmokeJob,
            BatchProperties batchProperties,
            JobParameterFactory jobParameterFactory
    ) {
        this.jobOperator = jobOperator;
        this.batchSmokeJob = batchSmokeJob;
        this.batchProperties = batchProperties;
        this.jobParameterFactory = jobParameterFactory;
    }

    @Scheduled(
            cron = "${batch.schedule.smoke.cron}",
            zone = "${batch.schedule.zone-id}"
    )
    public void runBatchSmokeJob() {
        if (!batchProperties.schedule().smoke().enabled()) {
            return;
        }

        try {
            JobParameters jobParameters = jobParameterFactory.createRunParameters(
                    BatchSmokeJobConfiguration.JOB_NAME
            );

            log.info("[Batch Smoke] Scheduler requests job execution.");

            JobExecution jobExecution = jobOperator.start(batchSmokeJob, jobParameters);

            log.info(
                    "[Batch Smoke] Job execution requested. status={}",
                    jobExecution.getStatus()
            );
        } catch (Exception exception) {
            log.error("[Batch Smoke] Failed to execute smoke job.", exception);
        }
    }
}
package org.profit.candle.batch.portfolio.eod.scheduler;

import java.time.Clock;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.portfolio.eod.job.PortfolioEodJobConfiguration;
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
        name = "batch.schedule.portfolio-eod.enabled",
        havingValue = "true"
)
public class PortfolioEodJobScheduler {

    private final JobOperator jobOperator;
    private final Job portfolioEodSnapshotJob;
    private final JobParameterFactory parameterFactory;
    private final Clock clock;

    public PortfolioEodJobScheduler(
            JobOperator jobOperator,
            @Qualifier(PortfolioEodJobConfiguration.JOB_NAME) Job portfolioEodSnapshotJob,
            JobParameterFactory parameterFactory,
            Clock clock
    ) {
        this.jobOperator = jobOperator;
        this.portfolioEodSnapshotJob = portfolioEodSnapshotJob;
        this.parameterFactory = parameterFactory;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${batch.schedule.portfolio-eod.cron}",
            zone = "${batch.schedule.zone-id}"
    )
    public void runPortfolioEodSnapshotJob() {
        LocalDate businessDate = LocalDate.now(clock);
        JobParameters parameters = parameterFactory.createDailyParameters(
                PortfolioEodJobConfiguration.JOB_NAME,
                businessDate
        );

        try {
            JobExecution execution = jobOperator.start(portfolioEodSnapshotJob, parameters);
            log.info(
                    "[Portfolio EOD] Job requested. businessDate={}, status={}",
                    businessDate,
                    execution.getStatus()
            );
        } catch (Exception exception) {
            log.error(
                    "[Portfolio EOD] Job request failed. businessDate={}",
                    businessDate,
                    exception
            );
        }
    }
}

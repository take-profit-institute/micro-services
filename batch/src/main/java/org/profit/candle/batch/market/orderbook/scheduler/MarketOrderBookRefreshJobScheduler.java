package org.profit.candle.batch.market.orderbook.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.market.orderbook.job.MarketOrderBookRefreshJobConfiguration;
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
        name = "batch.schedule.market-order-book.enabled",
        havingValue = "true"
)
public class MarketOrderBookRefreshJobScheduler {
    private final JobOperator jobOperator;
    private final Job job;
    private final JobParameterFactory parameterFactory;

    public MarketOrderBookRefreshJobScheduler(
            JobOperator jobOperator,
            @Qualifier(MarketOrderBookRefreshJobConfiguration.JOB_NAME) Job job,
            JobParameterFactory parameterFactory
    ) {
        this.jobOperator = jobOperator;
        this.job = job;
        this.parameterFactory = parameterFactory;
    }

    @Scheduled(
            cron = "${batch.schedule.market-order-book.cron}",
            zone = "${batch.schedule.zone-id}"
    )
    public void runMarketOrderBookRefreshJob() {
        try {
            JobParameters parameters = parameterFactory.createRunParameters(
                    MarketOrderBookRefreshJobConfiguration.JOB_NAME
            );
            JobExecution execution = jobOperator.start(job, parameters);
            log.info("[Market OrderBook] Job execution requested. status={}", execution.getStatus());
        } catch (Exception exception) {
            log.error("[Market OrderBook] Failed to execute job.", exception);
        }
    }
}

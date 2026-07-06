package org.profit.candle.batch.trading.scheduler;

import java.time.Clock;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.support.parameter.JobParameterFactory;
import org.profit.candle.batch.trading.job.TradingExpireReservationsJobConfiguration;
import org.profit.candle.batch.trading.job.TradingMarketCloseJobsConfiguration;
import org.profit.candle.batch.trading.job.TradingMorningJobsConfiguration;
import org.profit.candle.batch.trading.job.TradingTodayCloseJobConfiguration;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 거래일의 예약 및 주문 처리 Job을 정해진 장 운영 시각에 실행한다. */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "batch.schedule.trading.enabled",
        havingValue = "true"
)
public class TradingJobsScheduler {

    private final JobOperator jobOperator;
    private final Job previousCloseJob;
    private final Job openLimitJob;
    private final Job expirePendingOrdersJob;
    private final Job failStaleConvertingJob;
    private final Job todayCloseJob;
    private final Job expireReservationsJob;
    private final JobParameterFactory parameterFactory;
    private final Clock clock;

    public TradingJobsScheduler(
            JobOperator jobOperator,
            @Qualifier(TradingMorningJobsConfiguration.PREVIOUS_CLOSE_JOB_NAME) Job previousCloseJob,
            @Qualifier(TradingMorningJobsConfiguration.OPEN_LIMIT_JOB_NAME) Job openLimitJob,
            @Qualifier(TradingMarketCloseJobsConfiguration.EXPIRE_PENDING_JOB_NAME) Job expirePendingOrdersJob,
            @Qualifier(TradingMarketCloseJobsConfiguration.FAIL_STALE_JOB_NAME) Job failStaleConvertingJob,
            @Qualifier(TradingTodayCloseJobConfiguration.JOB_NAME) Job todayCloseJob,
            @Qualifier(TradingExpireReservationsJobConfiguration.JOB_NAME) Job expireReservationsJob,
            JobParameterFactory parameterFactory,
            Clock clock
    ) {
        this.jobOperator = jobOperator;
        this.previousCloseJob = previousCloseJob;
        this.openLimitJob = openLimitJob;
        this.expirePendingOrdersJob = expirePendingOrdersJob;
        this.failStaleConvertingJob = failStaleConvertingJob;
        this.todayCloseJob = todayCloseJob;
        this.expireReservationsJob = expireReservationsJob;
        this.parameterFactory = parameterFactory;
        this.clock = clock;
    }

    /** 08:30에 전일 종가 기준 예약을 처리한다. */
    @Scheduled(
            cron = "${batch.schedule.trading.previous-close-cron}",
            zone = "${batch.schedule.zone-id}"
    )
    public void runPreviousCloseJob() {
        start(previousCloseJob, TradingMorningJobsConfiguration.PREVIOUS_CLOSE_JOB_NAME);
    }

    /** 09:00에 장 시작 지정가 예약을 주문으로 전환한다. */
    @Scheduled(
            cron = "${batch.schedule.trading.open-limit-cron}",
            zone = "${batch.schedule.zone-id}"
    )
    public void runOpenLimitJob() {
        start(openLimitJob, TradingMorningJobsConfiguration.OPEN_LIMIT_JOB_NAME);
    }

    /** 15:30에 미체결 주문을 만료한 뒤 stale CONVERTING 예약을 정리한다. */
    @Scheduled(
            cron = "${batch.schedule.trading.market-close-cron}",
            zone = "${batch.schedule.zone-id}"
    )
    public void runMarketCloseJobs() {
        if (start(expirePendingOrdersJob, TradingMarketCloseJobsConfiguration.EXPIRE_PENDING_JOB_NAME)) {
            start(failStaleConvertingJob, TradingMarketCloseJobsConfiguration.FAIL_STALE_JOB_NAME);
        }
    }

    /** 15:40에 당일 종가 예약을 처리한 뒤 남은 예약을 만료한다. */
    @Scheduled(
            cron = "${batch.schedule.trading.today-close-cron}",
            zone = "${batch.schedule.zone-id}"
    )
    public void runTodayCloseJobs() {
        if (start(todayCloseJob, TradingTodayCloseJobConfiguration.JOB_NAME)) {
            start(expireReservationsJob, TradingExpireReservationsJobConfiguration.JOB_NAME);
        }
    }

    /** KST 거래일 파라미터로 Job을 시작하고 완료 여부를 반환한다. */
    private boolean start(Job job, String jobName) {
        LocalDate businessDate = LocalDate.now(clock);
        JobParameters parameters = parameterFactory.createDailyParameters(jobName, businessDate);

        try {
            JobExecution execution = jobOperator.start(job, parameters);
            boolean completed = execution.getStatus() == BatchStatus.COMPLETED;
            log.info(
                    "[Trading Batch] job={}, businessDate={}, status={}",
                    jobName,
                    businessDate,
                    execution.getStatus()
            );
            return completed;
        } catch (Exception exception) {
            log.error(
                    "[Trading Batch] job start failed. job={}, businessDate={}",
                    jobName,
                    businessDate,
                    exception
            );
            return false;
        }
    }
}

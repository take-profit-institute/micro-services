package org.profit.candle.batch.ranking.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.ranking.job.RankingFinalizeJobConfiguration;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 평일 16:20 KST에 일별 랭킹 확정 Job을 요청한다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "batch.ranking.enabled", havingValue = "true")
public class RankingFinalizeJobScheduler {

    private final JobOperator jobOperator;
    private final Job rankingFinalizeJob;
    private final Clock clock;

    public RankingFinalizeJobScheduler(
            JobOperator jobOperator,
            @Qualifier(RankingFinalizeJobConfiguration.JOB_NAME) Job rankingFinalizeJob,
            Clock clock
    ) {
        this.jobOperator = jobOperator;
        this.rankingFinalizeJob = rankingFinalizeJob;
        this.clock = clock;
    }

    /** 현재 KST 거래일을 식별값으로 사용해 자동 실행한다. */
    @Scheduled(cron = "${batch.ranking.cron}", zone = "${batch.schedule.zone-id}")
    public void runDailyRankingFinalizeJob() {
        LocalDate rankingDate = LocalDate.now(clock);
        try {
            JobExecution execution = jobOperator.start(
                    rankingFinalizeJob,
                    parameters(rankingDate)
            );
            log.info(
                    "[Daily Ranking] Job requested. rankingDate={}, status={}",
                    rankingDate,
                    execution.getStatus()
            );
        } catch (Exception exception) {
            log.error(
                    "[Daily Ranking] Job request failed. rankingDate={}",
                    rankingDate,
                    exception
            );
        }
    }

    /** 같은 날짜가 같은 JobInstance가 되도록 식별 파라미터를 생성한다. */
    private JobParameters parameters(LocalDate rankingDate) {
        return new JobParametersBuilder()
                .addString("jobName", RankingFinalizeJobConfiguration.JOB_NAME)
                .addString("rankingDate", rankingDate.toString())
                .addString("requestedAt", Instant.now(clock).toString(), false)
                .toJobParameters();
    }
}

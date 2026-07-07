package org.profit.candle.batch.ranking.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.profit.candle.batch.ranking.job.RankingFinalizeJobConfiguration;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

class RankingFinalizeJobSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-06T07:20:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    /** 자동 실행이 KST 거래일을 Ranking Job의 식별 파라미터로 전달하는지 검증한다. */
    @Test
    void startsJobWithKstRankingDate() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        Job job = mock(Job.class);
        when(operator.start(eq(job), any())).thenReturn(mock(JobExecution.class));
        RankingFinalizeJobScheduler scheduler = new RankingFinalizeJobScheduler(
                operator,
                job,
                CLOCK
        );

        scheduler.runDailyRankingFinalizeJob();

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(operator).start(eq(job), captor.capture());
        assertThat(captor.getValue().getString("jobName"))
                .isEqualTo(RankingFinalizeJobConfiguration.JOB_NAME);
        assertThat(captor.getValue().getString("rankingDate")).isEqualTo("2026-07-06");
    }
}

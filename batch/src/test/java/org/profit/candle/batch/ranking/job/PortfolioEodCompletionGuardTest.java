package org.profit.candle.batch.ranking.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.job.PortfolioEodJobConfiguration;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;

class PortfolioEodCompletionGuardTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    /** runId가 붙은 수동 EOD 실행도 거래일과 완료 상태로 인정한다. */
    @Test
    void findsCompletedExecutionByBusinessDate() {
        JobRepository repository = mock(JobRepository.class);
        JobInstance instance = mock(JobInstance.class);
        JobExecution execution = execution(DATE, BatchStatus.COMPLETED);
        when(repository.getJobInstances(
                PortfolioEodJobConfiguration.JOB_NAME,
                0,
                100
        )).thenReturn(List.of(instance));
        when(repository.getJobExecutions(instance)).thenReturn(List.of(execution));

        PortfolioEodCompletionGuard guard = new PortfolioEodCompletionGuard(repository);

        assertThat(guard.completed(DATE)).isTrue();
    }

    /** 같은 거래일 실행이 실패했으면 선행 Job 완료로 보지 않는다. */
    @Test
    void rejectsFailedExecution() {
        JobRepository repository = mock(JobRepository.class);
        JobInstance instance = mock(JobInstance.class);
        JobExecution execution = execution(DATE, BatchStatus.FAILED);
        when(repository.getJobInstances(
                PortfolioEodJobConfiguration.JOB_NAME,
                0,
                100
        )).thenReturn(List.of(instance));
        when(repository.getJobExecutions(instance)).thenReturn(List.of(execution));

        PortfolioEodCompletionGuard guard = new PortfolioEodCompletionGuard(repository);

        assertThat(guard.completed(DATE)).isFalse();
    }

    /** 테스트용 EOD 실행 메타데이터를 생성한다. */
    private JobExecution execution(LocalDate businessDate, BatchStatus status) {
        JobExecution execution = mock(JobExecution.class);
        when(execution.getJobParameters()).thenReturn(new JobParametersBuilder()
                .addString("jobName", PortfolioEodJobConfiguration.JOB_NAME)
                .addString("businessDate", businessDate.toString())
                .addLong("runId", 1L)
                .toJobParameters());
        when(execution.getStatus()).thenReturn(status);
        return execution;
    }
}

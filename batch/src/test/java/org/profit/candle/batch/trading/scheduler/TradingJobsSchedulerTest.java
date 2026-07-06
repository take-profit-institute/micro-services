package org.profit.candle.batch.trading.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.profit.candle.batch.support.parameter.JobParameterFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;

class TradingJobsSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-06T06:30:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private JobOperator jobOperator;
    private Job previousCloseJob;
    private Job openLimitJob;
    private Job expirePendingOrdersJob;
    private Job failStaleConvertingJob;
    private Job todayCloseJob;
    private Job expireReservationsJob;
    private TradingJobsScheduler scheduler;

    @BeforeEach
    void setUp() {
        jobOperator = mock(JobOperator.class);
        previousCloseJob = mock(Job.class);
        openLimitJob = mock(Job.class);
        expirePendingOrdersJob = mock(Job.class);
        failStaleConvertingJob = mock(Job.class);
        todayCloseJob = mock(Job.class);
        expireReservationsJob = mock(Job.class);
        scheduler = new TradingJobsScheduler(
                jobOperator,
                previousCloseJob,
                openLimitJob,
                expirePendingOrdersJob,
                failStaleConvertingJob,
                todayCloseJob,
                expireReservationsJob,
                new JobParameterFactory(CLOCK),
                CLOCK
        );
    }

    /** 오전 두 스케줄이 각자의 Job만 시작하는지 검증한다. */
    @Test
    void startsMorningJobsIndependently() throws Exception {
        JobExecution completedExecution = execution(BatchStatus.COMPLETED);
        when(jobOperator.start(any(Job.class), any())).thenReturn(completedExecution);

        scheduler.runPreviousCloseJob();
        scheduler.runOpenLimitJob();

        verify(jobOperator).start(eq(previousCloseJob), any());
        verify(jobOperator).start(eq(openLimitJob), any());
    }

    /** 15:30 선행 Job 성공 후 stale 정리 Job이 순서대로 실행되는지 검증한다. */
    @Test
    void runsMarketCloseJobsInOrder() throws Exception {
        JobExecution completedExecution = execution(BatchStatus.COMPLETED);
        when(jobOperator.start(any(Job.class), any())).thenReturn(completedExecution);

        scheduler.runMarketCloseJobs();

        InOrder order = inOrder(jobOperator);
        order.verify(jobOperator).start(eq(expirePendingOrdersJob), any());
        order.verify(jobOperator).start(eq(failStaleConvertingJob), any());
    }

    /** 15:40 선행 Job 성공 후 남은 예약 만료 Job이 순서대로 실행되는지 검증한다. */
    @Test
    void runsTodayCloseJobsInOrder() throws Exception {
        JobExecution completedExecution = execution(BatchStatus.COMPLETED);
        when(jobOperator.start(any(Job.class), any())).thenReturn(completedExecution);

        scheduler.runTodayCloseJobs();

        InOrder order = inOrder(jobOperator);
        order.verify(jobOperator).start(eq(todayCloseJob), any());
        order.verify(jobOperator).start(eq(expireReservationsJob), any());
    }

    /** 선행 Job 실패 시 의존하는 후행 Job을 시작하지 않는지 검증한다. */
    @Test
    void doesNotStartDependentJobWhenPreviousJobFails() throws Exception {
        JobExecution failedExecution = execution(BatchStatus.FAILED);
        when(jobOperator.start(eq(expirePendingOrdersJob), any())).thenReturn(failedExecution);

        scheduler.runMarketCloseJobs();

        verify(jobOperator, never()).start(eq(failStaleConvertingJob), any());
    }

    /** 지정한 상태의 Job 실행 결과를 생성한다. */
    private JobExecution execution(BatchStatus status) {
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(status);
        return execution;
    }
}

package org.profit.candle.batch.control.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.stub.StreamObserver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.trading.job.TradingExpireReservationsJobConfiguration;
import org.profit.candle.batch.trading.job.TradingMarketCloseJobsConfiguration;
import org.profit.candle.batch.trading.job.TradingMorningJobsConfiguration;
import org.profit.candle.batch.trading.job.TradingTodayCloseJobConfiguration;
import org.profit.candle.proto.batch.v1.BatchJob;
import org.profit.candle.proto.batch.v1.ListJobsRequest;
import org.profit.candle.proto.batch.v1.ListJobsResponse;
import org.profit.candle.proto.batch.v1.TriggerJobRequest;
import org.profit.candle.proto.batch.v1.TriggerJobResponse;
import org.profit.candle.proto.common.v1.CommandMetadata;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

class BatchControlTradingJobsTest {

    /** Batch Control이 6개 Trading Job을 모두 수동 실행 대상으로 제공하는지 검증한다. */
    @Test
    void listsAllTradingJobsAsTriggerable() {
        JobRegistry jobRegistry = mock(JobRegistry.class);
        when(jobRegistry.getJob(anyString())).thenReturn(mock(Job.class));
        BatchControlGrpcService service = new BatchControlGrpcService(
                mock(JobOperator.class),
                mock(),
                jobRegistry,
                Clock.systemUTC()
        );
        ResponseObserver<ListJobsResponse> observer = new ResponseObserver<>();

        service.listJobs(ListJobsRequest.getDefaultInstance(), observer);

        List<BatchJob> tradingJobs = observer.singleValue()
                .getJobsList()
                .stream()
                .filter(job -> job.getName().startsWith("trading"))
                .toList();
        assertThat(tradingJobs).allMatch(job -> job.getTriggerable()
                && job.getSupportedParametersList().contains("businessDate"));
        assertThat(tradingJobs.stream().map(BatchJob::getName).toList())
                .containsExactly(
                        TradingMorningJobsConfiguration.PREVIOUS_CLOSE_JOB_NAME,
                        TradingMorningJobsConfiguration.OPEN_LIMIT_JOB_NAME,
                        TradingMarketCloseJobsConfiguration.EXPIRE_PENDING_JOB_NAME,
                        TradingMarketCloseJobsConfiguration.FAIL_STALE_JOB_NAME,
                        TradingTodayCloseJobConfiguration.JOB_NAME,
                        TradingExpireReservationsJobConfiguration.JOB_NAME
                );
    }

    /** Trading Job 수동 실행 요청에 거래일 파라미터가 전달되는지 검증한다. */
    @Test
    void triggersTradingJobWithBusinessDate() throws Exception {
        String jobName = TradingMorningJobsConfiguration.PREVIOUS_CLOSE_JOB_NAME;
        Job job = mock(Job.class);
        JobRegistry jobRegistry = mock(JobRegistry.class);
        JobOperator jobOperator = mock(JobOperator.class);
        when(jobRegistry.getJob(jobName)).thenReturn(job);
        when(jobOperator.start(eq(job), any()))
                .thenAnswer(invocation -> execution(
                        jobName,
                        invocation.getArgument(1, JobParameters.class)
                ));
        BatchControlGrpcService service = new BatchControlGrpcService(
                jobOperator,
                mock(),
                jobRegistry,
                Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneOffset.UTC)
        );
        ResponseObserver<TriggerJobResponse> observer = new ResponseObserver<>();
        TriggerJobRequest request = TriggerJobRequest.newBuilder()
                .setJobName(jobName)
                .putParameters("businessDate", "2026-07-06")
                .setCommandMetadata(CommandMetadata.newBuilder()
                        .setIdempotencyKey("manual-trading-20260706")
                        .build())
                .build();

        service.triggerJob(request, observer);

        assertThat(observer.singleValue().getExecution().getParametersMap())
                .containsEntry("businessDate", "2026-07-06");
    }

    /** Batch Control 응답에 필요한 완료 실행 정보를 생성한다. */
    private JobExecution execution(String jobName, JobParameters parameters) {
        JobInstance instance = mock(JobInstance.class);
        when(instance.getJobName()).thenReturn(jobName);
        JobExecution execution = mock(JobExecution.class);
        when(execution.getId()).thenReturn(1L);
        when(execution.getJobInstanceId()).thenReturn(1L);
        when(execution.getJobInstance()).thenReturn(instance);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(execution.getJobParameters()).thenReturn(parameters);
        when(execution.getCreateTime()).thenReturn(LocalDateTime.of(2026, 7, 6, 9, 0));
        when(execution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);
        return execution;
    }

    /** 단일 응답 gRPC 호출 결과를 테스트에서 수집한다. */
    private static final class ResponseObserver<T> implements StreamObserver<T> {

        private final List<T> values = new ArrayList<>();

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError(throwable);
        }

        @Override
        public void onCompleted() {
        }

        private T singleValue() {
            assertThat(values).hasSize(1);
            return values.getFirst();
        }
    }
}

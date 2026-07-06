package org.profit.candle.batch.control.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.profit.candle.batch.ranking.job.RankingFinalizeJobConfiguration;
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

class BatchControlRankingJobTest {

    /** Batch Control 목록에 Ranking Job과 수동 실행 파라미터를 노출한다. */
    @Test
    void listsRankingJobAsTriggerable() throws Exception {
        JobRegistry registry = mock(JobRegistry.class);
        when(registry.getJob(RankingFinalizeJobConfiguration.JOB_NAME))
                .thenReturn(mock(Job.class));
        BatchControlGrpcService service = service(mock(JobOperator.class), registry);
        ResponseObserver<ListJobsResponse> observer = new ResponseObserver<>();

        service.listJobs(ListJobsRequest.getDefaultInstance(), observer);

        BatchJob rankingJob = observer.singleValue().getJobsList().stream()
                .filter(job -> RankingFinalizeJobConfiguration.JOB_NAME.equals(job.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(rankingJob.getTriggerable()).isTrue();
        assertThat(rankingJob.getSupportedParametersList()).containsExactly("rankingDate");
    }

    /** 수동 실행 요청의 rankingDate가 Job 식별 파라미터로 전달되는지 검증한다. */
    @Test
    void triggersRankingJobWithRankingDate() throws Exception {
        Job job = mock(Job.class);
        JobRegistry registry = mock(JobRegistry.class);
        JobOperator operator = mock(JobOperator.class);
        when(registry.getJob(RankingFinalizeJobConfiguration.JOB_NAME)).thenReturn(job);
        when(operator.start(eq(job), any())).thenAnswer(invocation -> execution(
                invocation.getArgument(1, JobParameters.class)
        ));
        BatchControlGrpcService service = service(operator, registry);
        ResponseObserver<TriggerJobResponse> observer = new ResponseObserver<>();
        TriggerJobRequest request = TriggerJobRequest.newBuilder()
                .setJobName(RankingFinalizeJobConfiguration.JOB_NAME)
                .putParameters("rankingDate", "2026-07-06")
                .setCommandMetadata(CommandMetadata.newBuilder()
                        .setIdempotencyKey("manual-ranking-20260706")
                        .build())
                .build();

        service.triggerJob(request, observer);

        assertThat(observer.singleValue().getExecution().getParametersMap())
                .containsEntry("rankingDate", "2026-07-06");
    }

    /** 고정 시각을 사용하는 Batch Control 서비스를 생성한다. */
    private BatchControlGrpcService service(JobOperator operator, JobRegistry registry) {
        return new BatchControlGrpcService(
                operator,
                mock(),
                registry,
                Clock.fixed(Instant.parse("2026-07-06T07:20:00Z"), ZoneOffset.UTC)
        );
    }

    /** 수동 실행 응답에 사용할 완료 Job 실행을 생성한다. */
    private JobExecution execution(JobParameters parameters) {
        JobInstance instance = mock(JobInstance.class);
        when(instance.getJobName()).thenReturn(RankingFinalizeJobConfiguration.JOB_NAME);
        JobExecution execution = mock(JobExecution.class);
        when(execution.getId()).thenReturn(1L);
        when(execution.getJobInstanceId()).thenReturn(1L);
        when(execution.getJobInstance()).thenReturn(instance);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(execution.getJobParameters()).thenReturn(parameters);
        when(execution.getCreateTime()).thenReturn(LocalDateTime.of(2026, 7, 6, 16, 20));
        when(execution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);
        return execution;
    }

    /** 단일 gRPC 응답을 테스트에서 수집한다. */
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

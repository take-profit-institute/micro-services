package org.profit.candle.batch.control.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.profit.candle.batch.portfolio.eod.job.PortfolioEodJobConfiguration;
import org.profit.candle.batch.smoke.job.BatchSmokeJobConfiguration;
import org.profit.candle.batch.stock.sync.job.StockSyncJobConfiguration;
import org.profit.candle.proto.batch.v1.BatchControlServiceGrpc;
import org.profit.candle.proto.batch.v1.BatchJob;
import org.profit.candle.proto.batch.v1.GetJobExecutionRequest;
import org.profit.candle.proto.batch.v1.GetJobExecutionResponse;
import org.profit.candle.proto.batch.v1.JobExecutionResponse;
import org.profit.candle.proto.batch.v1.JobExecutionStatus;
import org.profit.candle.proto.batch.v1.ListJobExecutionsRequest;
import org.profit.candle.proto.batch.v1.ListJobExecutionsResponse;
import org.profit.candle.proto.batch.v1.ListJobsRequest;
import org.profit.candle.proto.batch.v1.ListJobsResponse;
import org.profit.candle.proto.batch.v1.TriggerJobRequest;
import org.profit.candle.proto.batch.v1.TriggerJobResponse;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchControlGrpcService extends BatchControlServiceGrpc.BatchControlServiceImplBase {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");
    private static final Set<String> ALLOWED_JOBS = Set.of(
            BatchSmokeJobConfiguration.JOB_NAME,
            PortfolioEodJobConfiguration.JOB_NAME,
            StockSyncJobConfiguration.JOB_NAME
    );

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final JobRegistry jobRegistry;
    private final Clock clock;

    @Override
    public void listJobs(ListJobsRequest request, StreamObserver<ListJobsResponse> observer) {
        ListJobsResponse response = ListJobsResponse.newBuilder()
                .addJobs(BatchJob.newBuilder()
                        .setName(BatchSmokeJobConfiguration.JOB_NAME)
                        .setDescription("Smoke test job for batch infrastructure")
                        .addSupportedParameters("runId")
                        .setTriggerable(isRegistered(BatchSmokeJobConfiguration.JOB_NAME))
                        .build())
                .addJobs(BatchJob.newBuilder()
                        .setName(PortfolioEodJobConfiguration.JOB_NAME)
                        .setDescription("Portfolio end-of-day snapshot generation")
                        .addSupportedParameters("businessDate")
                        .setTriggerable(isRegistered(PortfolioEodJobConfiguration.JOB_NAME))
                        .build())
                .addJobs(BatchJob.newBuilder()
                        .setName(StockSyncJobConfiguration.JOB_NAME)
                        .setDescription("Synchronize KOSPI and KOSDAQ stock catalogs")
                        .addSupportedParameters("runId")
                        .setTriggerable(isRegistered(StockSyncJobConfiguration.JOB_NAME))
                        .build())
                .build();
        observer.onNext(response);
        observer.onCompleted();
    }

    @Override
    public void triggerJob(TriggerJobRequest request, StreamObserver<TriggerJobResponse> observer) {
        try {
            String jobName = requireAllowedJob(request.getJobName());
            String idempotencyKey = requireIdempotencyKey(
                    request.hasCommandMetadata() ? request.getCommandMetadata().getIdempotencyKey() : ""
            );
            Job job = jobRegistry.getJob(jobName);
            JobParameters parameters = toJobParameters(jobName, request.getParametersMap(), idempotencyKey);

            JobExecution execution = jobOperator.start(job, parameters);
            observer.onNext(TriggerJobResponse.newBuilder()
                    .setExecution(toResponse(execution))
                    .build());
            observer.onCompleted();
        } catch (IllegalArgumentException exception) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asRuntimeException());
        } catch (org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException exception) {
            observer.onError(Status.ALREADY_EXISTS.withDescription("BATCH_JOB_ALREADY_COMPLETED").asRuntimeException());
        } catch (JobExecutionException exception) {
            observer.onError(Status.FAILED_PRECONDITION.withDescription("BATCH_JOB_START_FAILED").asRuntimeException());
        } catch (Exception exception) {
            observer.onError(Status.INTERNAL.withDescription("BATCH_INTERNAL_ERROR").asRuntimeException());
        }
    }

    @Override
    public void getJobExecution(GetJobExecutionRequest request, StreamObserver<GetJobExecutionResponse> observer) {
        JobExecution execution = jobRepository.getJobExecution(request.getExecutionId());
        if (execution == null) {
            observer.onError(Status.NOT_FOUND.withDescription("BATCH_EXECUTION_NOT_FOUND").asRuntimeException());
            return;
        }
        observer.onNext(GetJobExecutionResponse.newBuilder()
                .setExecution(toResponse(execution))
                .build());
        observer.onCompleted();
    }

    @Override
    public void listJobExecutions(
            ListJobExecutionsRequest request,
            StreamObserver<ListJobExecutionsResponse> observer
    ) {
        try {
            String jobName = requireAllowedJob(request.getJobName());
            int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 100) : 20;

            List<JobExecutionResponse> executions = jobRepository.getJobInstances(jobName, 0, limit).stream()
                    .flatMap(instance -> jobRepository.getJobExecutions(instance).stream())
                    .sorted(Comparator.comparing(JobExecution::getCreateTime).reversed())
                    .limit(limit)
                    .map(this::toResponse)
                    .toList();

            observer.onNext(ListJobExecutionsResponse.newBuilder()
                    .addAllExecutions(executions)
                    .build());
            observer.onCompleted();
        } catch (IllegalArgumentException exception) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asRuntimeException());
        }
    }

    private JobParameters toJobParameters(
            String jobName,
            Map<String, String> input,
            String idempotencyKey
    ) {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addString("jobName", jobName)
                .addString("requestedAt", Instant.now(clock).toString(), false)
                .addString("idempotencyKey", idempotencyKey, false);

        if (PortfolioEodJobConfiguration.JOB_NAME.equals(jobName)) {
            String businessDate = input.getOrDefault("businessDate", LocalDate.now(clock).toString());
            validateDate(businessDate);
            builder.addString("businessDate", businessDate);
            if (input.containsKey("runId")) {
                builder.addLong("runId", parseLong(input.get("runId"), "runId"));
            }
            return builder.toJobParameters();
        }

        if (BatchSmokeJobConfiguration.JOB_NAME.equals(jobName)
                || StockSyncJobConfiguration.JOB_NAME.equals(jobName)) {
            builder.addString("businessDate", input.getOrDefault("businessDate", LocalDate.now(clock).toString()));
            builder.addLong("runId", input.containsKey("runId")
                    ? parseLong(input.get("runId"), "runId")
                    : clock.millis());
            return builder.toJobParameters();
        }

        throw new IllegalArgumentException("BATCH_JOB_NOT_ALLOWED");
    }

    private String requireAllowedJob(String jobName) {
        if (jobName == null || jobName.isBlank() || !ALLOWED_JOBS.contains(jobName)) {
            throw new IllegalArgumentException("BATCH_JOB_NOT_ALLOWED");
        }
        return jobName;
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED");
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("IDEMPOTENCY_KEY_INVALID");
        }
        return value;
    }

    private boolean isRegistered(String jobName) {
        try {
            jobRegistry.getJob(jobName);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void validateDate(String value) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("BATCH_BUSINESS_DATE_INVALID");
        }
    }

    private long parseLong(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("BATCH_PARAMETER_INVALID:" + field);
        }
    }

    private JobExecutionResponse toResponse(JobExecution execution) {
        JobExecutionResponse.Builder builder = JobExecutionResponse.newBuilder()
                .setExecutionId(execution.getId())
                .setInstanceId(execution.getJobInstanceId())
                .setJobName(execution.getJobInstance().getJobName())
                .setStatus(toStatus(execution.getStatus()))
                .putAllParameters(toParameterMap(execution.getJobParameters()))
                .setCreateTime(toTimestamp(execution.getCreateTime()))
                .setExitCode(execution.getExitStatus().getExitCode())
                .setExitDescription(execution.getExitStatus().getExitDescription());

        if (execution.getStartTime() != null) {
            builder.setStartTime(toTimestamp(execution.getStartTime()));
        }
        if (execution.getEndTime() != null) {
            builder.setEndTime(toTimestamp(execution.getEndTime()));
        }
        if (execution.getLastUpdated() != null) {
            builder.setLastUpdated(toTimestamp(execution.getLastUpdated()));
        }
        return builder.build();
    }

    private Map<String, String> toParameterMap(JobParameters parameters) {
        return parameters.parameters().stream()
                .collect(java.util.stream.Collectors.toMap(
                        JobParameter::name,
                        parameter -> String.valueOf(parameter.value())
                ));
    }

    private JobExecutionStatus toStatus(BatchStatus status) {
        return switch (status) {
            case STARTING -> JobExecutionStatus.JOB_EXECUTION_STATUS_STARTING;
            case STARTED -> JobExecutionStatus.JOB_EXECUTION_STATUS_STARTED;
            case STOPPING -> JobExecutionStatus.JOB_EXECUTION_STATUS_STOPPING;
            case STOPPED -> JobExecutionStatus.JOB_EXECUTION_STATUS_STOPPED;
            case FAILED -> JobExecutionStatus.JOB_EXECUTION_STATUS_FAILED;
            case COMPLETED -> JobExecutionStatus.JOB_EXECUTION_STATUS_COMPLETED;
            case ABANDONED -> JobExecutionStatus.JOB_EXECUTION_STATUS_ABANDONED;
            case UNKNOWN -> JobExecutionStatus.JOB_EXECUTION_STATUS_UNKNOWN;
        };
    }

    private Timestamp toTimestamp(LocalDateTime time) {
        Instant instant = time.toInstant(ZoneOffset.UTC);
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}

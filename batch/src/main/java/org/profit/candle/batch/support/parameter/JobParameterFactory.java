package org.profit.candle.batch.support.parameter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobParameterFactory {

    private final Clock clock;

    public JobParameters createRunParameters(String jobName) {
        Instant requestedAt = Instant.now(clock);
        LocalDate businessDate = LocalDate.now(clock);

        return new JobParametersBuilder()
                .addString("jobName", jobName)
                .addString("businessDate", businessDate.toString())
                .addLong("runId", clock.millis())
                .addString("requestedAt", requestedAt.toString(), false)
                .toJobParameters();
    }

    public JobParameters createDailyParameters(String jobName, LocalDate businessDate) {
        Instant requestedAt = Instant.now(clock);

        return new JobParametersBuilder()
                .addString("jobName", jobName)
                .addString("businessDate", businessDate.toString())
                .addLong("runId", System.nanoTime())
                .addString("requestedAt", requestedAt.toString(), false)
                .toJobParameters();
    }
}

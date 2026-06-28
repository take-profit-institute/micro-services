package org.profit.candle.batch.smoke.job;

import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.batch.support.listener.BatchLoggingListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BatchSmokeJobConfiguration {

    public static final String JOB_NAME = "batchSmokeJob";
    public static final String STEP_NAME = "batchSmokeStep";

    private static final int CHUNK_SIZE = 2;

    @Bean(name = JOB_NAME)
    public Job batchSmokeJob(
            JobRepository jobRepository,
            @Qualifier(STEP_NAME) Step batchSmokeStep,
            BatchLoggingListener batchLoggingListener
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(batchLoggingListener)
                .start(batchSmokeStep)
                .build();
    }

    @Bean(name = STEP_NAME)
    public Step batchSmokeStep(
            JobRepository jobRepository,
            @Qualifier("batchSmokeItemReader") ItemReader<String> reader,
            @Qualifier("batchSmokeItemProcessor") ItemProcessor<String, String> processor,
            @Qualifier("batchSmokeItemWriter") ItemWriter<String> writer
    ) {
        return new ChunkOrientedStepBuilder<String, String>(STEP_NAME, jobRepository, CHUNK_SIZE)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<String> batchSmokeItemReader() {
        return new ListItemReader<>(List.of(
                "portfolio snapshot",
                "ranking aggregation",
                "cleanup job",
                "batch metadata"
        ));
    }

    @Bean
    public ItemProcessor<String, String> batchSmokeItemProcessor() {
        return item -> item.toUpperCase(Locale.ROOT);
    }

    @Bean
    public ItemWriter<String> batchSmokeItemWriter() {
        return items -> {
            log.info("[Batch Smoke] chunk write start");

            for (String item : items) {
                log.info("[Batch Smoke] item={}", item);
            }

            log.info("[Batch Smoke] chunk write end");
        };
    }
}
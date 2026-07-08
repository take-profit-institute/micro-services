package org.profit.candle.batch.config;

import java.time.Clock;
import java.time.ZoneId;
import org.profit.candle.batch.ranking.config.RankingBatchProperties;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.boot.batch.autoconfigure.BatchTaskExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties({BatchProperties.class, RankingBatchProperties.class})
public class BatchConfiguration {

    @Bean
    public Clock batchClock(BatchProperties batchProperties) {
        return Clock.system(ZoneId.of(batchProperties.schedule().zoneId()));
    }

    @Bean
    public JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    /**
     * Boot 자동설정 JobOperator가 잡을 띄울 때 쓸 executor. {@code @BatchTaskExecutor} 자격으로 노출하면
     * 기본 SyncTaskExecutor 대신 이 풀이 쓰여 {@code jobOperator.start()}가 즉시 반환하고 잡 본체는
     * 별도 스레드에서 돈다 — inbound gRPC(triggerJob)의 Context/deadline이 잡의 자식 RPC로 전파되지 않아
     * 호출자 타임아웃에 잡 전체가 CANCELLED로 죽던 문제를 없앤다.
     */
    @Bean
    @BatchTaskExecutor
    public TaskExecutor batchJobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("batch-job-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}

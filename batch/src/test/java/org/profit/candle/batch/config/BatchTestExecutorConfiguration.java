package org.profit.candle.batch.config;

import org.springframework.boot.batch.autoconfigure.BatchTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * 프로덕션은 잡을 async로 띄우지만(=triggerJob이 즉시 반환), 통합 테스트는 {@code jobOperator.start()}
 * 직후 실행 상태를 단언하거나 곧바로 재시작하므로 결정적(동기) 실행이 필요하다. 테스트 클래스패스에서만
 * {@code @BatchTaskExecutor} executor를 SyncTaskExecutor로 덮어 자동설정 JobOperator를 동기로 돌린다.
 * (@SpringBootTest 컴포넌트 스캔 대상 패키지라 별도 @Import 없이 모든 배치 통합 테스트에 적용된다.)
 */
@Configuration
public class BatchTestExecutorConfiguration {

    @Bean
    @Primary
    @BatchTaskExecutor
    public TaskExecutor syncBatchTaskExecutor() {
        return new SyncTaskExecutor();
    }
}

package org.profit.candle.batch.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.JobOperatorFactoryBean;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BatchProperties.class)
public class BatchConfiguration {

    @Bean
    public Clock batchClock(BatchProperties batchProperties) {
        return Clock.system(ZoneId.of(batchProperties.schedule().zoneId()));
    }

    @Bean
    @ConditionalOnMissingBean(JobOperator.class)
    public JobOperatorFactoryBean jobOperator(JobRepository jobRepository) {
        JobOperatorFactoryBean factoryBean = new JobOperatorFactoryBean();
        factoryBean.setJobRepository(jobRepository);
        return factoryBean;
    }
}
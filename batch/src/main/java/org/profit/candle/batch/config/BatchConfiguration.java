package org.profit.candle.batch.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
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
    public JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }
}

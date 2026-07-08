package org.profit.candle.news.collector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
class NewsCollectionClockConfig {
    static final ZoneId NEWS_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    Clock newsCollectionClock() {
        return Clock.system(NEWS_ZONE);
    }
}

package org.profit.candle.ranking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@EnableKafka
@EnableScheduling
@SpringBootApplication
public class RankingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RankingServiceApplication.class, args);
    }

    /** 일관된 현재 시각을 사용하고 테스트에서 교체할 수 있도록 UTC Clock을 제공한다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

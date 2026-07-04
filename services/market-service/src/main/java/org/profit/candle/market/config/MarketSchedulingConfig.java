package org.profit.candle.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * WS 세션 게이팅 스케줄러 활성화 + 시각 소스(Clock) 빈.
 * Clock 을 빈으로 두면 MarketSession 의 세션창 판정을 고정 시각으로 단위 테스트할 수 있다.
 */
@Configuration
@EnableScheduling
public class MarketSchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

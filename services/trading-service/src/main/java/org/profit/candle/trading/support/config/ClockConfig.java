package org.profit.candle.trading.support.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시간 의존 로직(거래 시간 검증, 예약 주문 배치 트리거 등)을 테스트 가능하게
 * 만들기 위한 {@link Clock} 빈. 코드에서 {@code Instant.now()}/{@code LocalTime.now()}를
 * 직접 호출하지 않고, 이 빈을 주입받아 호출하도록 한다 — 테스트에서
 * {@code Clock.fixed(...)}로 교체해 특정 시각을 고정할 수 있다.
 */

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

package org.profit.candle.batch.portfolio.eod.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FixedSeedCapitalProviderTest {

    /** 입출금 기능 도입 전 합의된 초기 원금 1억 원을 반환하는지 검증한다. */
    @Test
    void returnsOneHundredMillionKrw() {
        FixedSeedCapitalProvider provider = new FixedSeedCapitalProvider();

        long seedCapital = provider.getSeedCapital(
                "00000000-0000-0000-0000-000000000001",
                LocalDate.of(2026, 7, 6)
        );

        assertThat(seedCapital).isEqualTo(100_000_000L);
    }
}

package org.profit.candle.stock.chart.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CandleIdTest {

    @Test
    void equalsAndHashCode_useCompositeKeyFields() {
        Instant openTime = Instant.parse("2026-06-30T00:00:00Z");
        CandleId first = new CandleId("005930", "1d", openTime);
        CandleId second = new CandleId("005930", "1d", openTime);
        CandleId different = new CandleId("005930", "1w", openTime);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first).isNotEqualTo(different);
    }
}

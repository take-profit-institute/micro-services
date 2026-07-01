package org.profit.candle.stock.chart.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandleEntityTest {

    @Test
    void applyPrices_updatesOhlcv() {
        CandleEntity candle = new CandleEntity("005930", "1d", Instant.parse("2026-06-30T00:00:00Z"));

        candle.applyPrices(70000, 71000, 69000, 70500, 1000, true, "KIWOOM");

        assertThat(candle.open()).isEqualTo(70000);
        assertThat(candle.high()).isEqualTo(71000);
        assertThat(candle.low()).isEqualTo(69000);
        assertThat(candle.close()).isEqualTo(70500);
        assertThat(candle.volume()).isEqualTo(1000);
        assertThat(candle.closed()).isTrue();
        assertThat(candle.source()).isEqualTo("KIWOOM");
    }

    @Test
    void applyPrices_rejectsInvalidRange() {
        CandleEntity candle = new CandleEntity("005930", "1d", Instant.parse("2026-06-30T00:00:00Z"));

        assertThatThrownBy(() -> candle.applyPrices(70000, 68000, 69000, 70500, 1000, true, "KIWOOM"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

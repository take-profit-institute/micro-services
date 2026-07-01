package org.profit.candle.wishlist.alert.service;

import org.junit.jupiter.api.Test;
import org.profit.candle.wishlist.alert.entity.AlertDirection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceChangeCalculatorTest {
    @Test
    void basisPoints_calculatesChangeFromOpenPrice() {
        assertThat(PriceChangeCalculator.basisPoints(10_000, 10_500)).isEqualTo(500);
        assertThat(PriceChangeCalculator.basisPoints(10_000, 9_500)).isEqualTo(-500);
    }

    @Test
    void direction_returnsRiseOrFallOnlyWhenThresholdReached() {
        assertThat(PriceChangeCalculator.direction(500, 500)).isEqualTo(AlertDirection.RISE);
        assertThat(PriceChangeCalculator.direction(-500, 500)).isEqualTo(AlertDirection.FALL);
        assertThat(PriceChangeCalculator.direction(499, 500)).isNull();
        assertThat(PriceChangeCalculator.direction(-499, 500)).isNull();
    }

    @Test
    void basisPoints_rejectsNonPositivePrices() {
        assertThatThrownBy(() -> PriceChangeCalculator.basisPoints(0, 10_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PriceChangeCalculator.basisPoints(10_000, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

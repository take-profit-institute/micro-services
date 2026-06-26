package org.profit.candle.portfolio.holding.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingEntityTest {

    private HoldingEntity entity() {
        return new HoldingEntity("u1", "005930", "삼성전자", "반도체", "KOSPI");
    }

    @Test
    void constructor_setsInitialStateInactive() {
        HoldingEntity h = entity();

        assertThat(h.userId()).isEqualTo("u1");
        assertThat(h.symbol()).isEqualTo("005930");
        assertThat(h.quantity()).isEqualTo(0);
        assertThat(h.averagePrice()).isEqualTo(0);
        assertThat(h.bookValue()).isEqualTo(0);
        assertThat(h.realizedProfit()).isEqualTo(0);
        assertThat(h.active()).isFalse();
    }

    @Test
    void applyBuy_firstPurchase_setsAveragePriceAndActivates() {
        HoldingEntity h = entity();

        h.applyBuy(10, 75_000);

        assertThat(h.quantity()).isEqualTo(10);
        assertThat(h.averagePrice()).isEqualTo(75_000);
        assertThat(h.bookValue()).isEqualTo(750_000);
        assertThat(h.cachedCurrentPrice()).isEqualTo(75_000);
        assertThat(h.active()).isTrue();
    }

    @Test
    void applyBuy_additionalPurchase_recalculatesWeightedAverage() {
        HoldingEntity h = entity();
        h.applyBuy(10, 75_000);

        h.applyBuy(5, 90_000); // (750000 + 450000) / 15 = 80000

        assertThat(h.quantity()).isEqualTo(15);
        assertThat(h.averagePrice()).isEqualTo(80_000);
        assertThat(h.bookValue()).isEqualTo(1_200_000);
    }

    @Test
    void applyBuy_refreshesUpdatedAt() throws InterruptedException {
        HoldingEntity h = entity();
        var before = h.updatedAt();
        Thread.sleep(2);

        h.applyBuy(10, 75_000);

        assertThat(h.updatedAt()).isAfter(before);
    }

    @Test
    void applySell_partialSell_realizesProfitAndKeepsActive() {
        HoldingEntity h = entity();
        h.applyBuy(10, 75_000);

        h.applySell(3, 80_000); // profit = (80000 - 75000) * 3 = 15000

        assertThat(h.quantity()).isEqualTo(7);
        assertThat(h.realizedProfit()).isEqualTo(15_000);
        assertThat(h.active()).isTrue();
        assertThat(h.bookValue()).isEqualTo(7 * 75_000);
    }

    @Test
    void applySell_fullSell_setsInactiveAndRealizesTotalProfit() {
        HoldingEntity h = entity();
        h.applyBuy(10, 75_000);

        h.applySell(10, 80_000); // profit = (80000 - 75000) * 10 = 50000

        assertThat(h.quantity()).isEqualTo(0);
        assertThat(h.active()).isFalse();
        assertThat(h.realizedProfit()).isEqualTo(50_000);
        assertThat(h.bookValue()).isEqualTo(0);
    }

    @Test
    void applySell_sellAtLoss_recordsNegativeProfit() {
        HoldingEntity h = entity();
        h.applyBuy(10, 75_000);

        h.applySell(5, 70_000); // profit = (70000 - 75000) * 5 = -25000

        assertThat(h.realizedProfit()).isEqualTo(-25_000);
    }

    @Test
    void applySell_cumulativeRealizedProfit_accumulatesAcrossSells() {
        HoldingEntity h = entity();
        h.applyBuy(10, 75_000);
        h.applySell(5, 80_000); // +25000
        h.applySell(5, 85_000); // +50000 → total = 75000

        assertThat(h.realizedProfit()).isEqualTo(75_000);
    }

    @Test
    void applySell_updatesCurrentPrice() {
        HoldingEntity h = entity();
        h.applyBuy(10, 75_000);

        h.applySell(3, 82_000);

        assertThat(h.cachedCurrentPrice()).isEqualTo(82_000);
    }

    @Test
    void updateCachedPrice_updatesWithoutAffectingOtherFields() {
        HoldingEntity h = entity();
        h.applyBuy(10, 75_000);

        h.updateCachedPrice(85_000);

        assertThat(h.cachedCurrentPrice()).isEqualTo(85_000);
        assertThat(h.averagePrice()).isEqualTo(75_000);
        assertThat(h.bookValue()).isEqualTo(750_000);
        assertThat(h.quantity()).isEqualTo(10);
    }
}

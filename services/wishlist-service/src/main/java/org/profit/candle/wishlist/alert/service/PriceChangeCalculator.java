package org.profit.candle.wishlist.alert.service;

import org.profit.candle.wishlist.alert.entity.AlertDirection;

public final class PriceChangeCalculator {
    private PriceChangeCalculator() {
        throw new AssertionError("Utility class");
    }

    public static int basisPoints(long openPrice, long price) {
        if (openPrice <= 0 || price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        long diff = price - openPrice;
        return Math.toIntExact(diff * 10_000 / openPrice);
    }

    public static AlertDirection direction(int changeBasisPoints, int thresholdBasisPoints) {
        if (changeBasisPoints >= thresholdBasisPoints) {
            return AlertDirection.RISE;
        }
        if (changeBasisPoints <= -thresholdBasisPoints) {
            return AlertDirection.FALL;
        }
        return null;
    }
}

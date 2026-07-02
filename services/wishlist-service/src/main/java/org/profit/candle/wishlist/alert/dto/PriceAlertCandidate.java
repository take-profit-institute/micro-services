package org.profit.candle.wishlist.alert.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.profit.candle.wishlist.alert.entity.AlertDirection;

public record PriceAlertCandidate(
        UUID alertId,
        UUID userId,
        String symbol,
        LocalDate tradingDate,
        AlertDirection direction,
        int thresholdBasisPoints,
        long openPrice,
        long triggerPrice,
        int changeBasisPoints,
        String idempotencyKey
) {
}

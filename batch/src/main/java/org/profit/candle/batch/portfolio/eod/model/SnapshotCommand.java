package org.profit.candle.batch.portfolio.eod.model;

import java.time.LocalDate;
import java.util.Map;

public record SnapshotCommand(
        String userId,
        LocalDate businessDate,
        long totalAsset,
        long stockValue,
        long seedCapital,
        String idempotencyKey
) {
    public record CalculationContext(
            LocalDate businessDate,
            long cash,
            long seedCapital,
            Map<String, ClosingPrice> closingPrices,
            String idempotencyKey
    ) {
        public CalculationContext {
            closingPrices = Map.copyOf(closingPrices);
        }
    }
}

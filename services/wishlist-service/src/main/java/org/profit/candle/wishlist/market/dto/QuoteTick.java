package org.profit.candle.wishlist.market.dto;

import java.time.Instant;
import java.time.LocalDate;

public record QuoteTick(
        String symbol,
        long price,
        long openPrice,
        String marketStatus,
        LocalDate tradingDate,
        Instant timestamp
) {
    public boolean open() {
        return marketStatus == null || marketStatus.isBlank() || "OPEN".equalsIgnoreCase(marketStatus);
    }
}

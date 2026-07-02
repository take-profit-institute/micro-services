package org.profit.candle.batch.portfolio.eod.model;

import java.time.Instant;

public record ClosingPrice(
        String symbol,
        long price,
        Instant quotedAt
) {
}

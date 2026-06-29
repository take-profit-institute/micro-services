package org.profit.candle.portfolio.holding.event.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderFilledPayload(
        UUID eventId,
        String eventType,
        int eventVersion,
        String userId,
        String symbol,
        String side,            // "BUY" | "SELL"
        long quantity,
        long executedPrice,
        Instant executedAt
) {}

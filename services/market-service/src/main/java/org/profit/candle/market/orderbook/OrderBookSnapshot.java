package org.profit.candle.market.orderbook;

import java.time.Instant;
import java.util.List;

public record OrderBookSnapshot(
        String symbol,
        long bestAskPrice,
        long bestAskQuantity,
        long bestBidPrice,
        long bestBidQuantity,
        long totalAskQuantity,
        long totalBidQuantity,
        Instant quotedAt,
        List<OrderBookLevelSnapshot> levels
) {
}

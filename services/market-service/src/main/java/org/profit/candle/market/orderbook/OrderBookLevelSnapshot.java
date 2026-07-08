package org.profit.candle.market.orderbook;

public record OrderBookLevelSnapshot(
        int level,
        long askPrice,
        long askQuantity,
        long bidPrice,
        long bidQuantity
) {
}

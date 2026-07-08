package org.profit.candle.batch.market.orderbook.client;

public interface MarketOrderBookRefreshClient {
    boolean isMarketOpen();

    Result refresh();

    record Result(
            int targetCount,
            int successCount,
            int failCount,
            boolean skipped,
            String reason
    ) {
    }
}

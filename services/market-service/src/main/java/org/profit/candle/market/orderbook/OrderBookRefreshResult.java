package org.profit.candle.market.orderbook;

public record OrderBookRefreshResult(
        int targetCount,
        int successCount,
        int failCount,
        boolean skipped,
        String reason
) {
    public static OrderBookRefreshResult skipped(String reason) {
        return new OrderBookRefreshResult(0, 0, 0, true, reason);
    }
}

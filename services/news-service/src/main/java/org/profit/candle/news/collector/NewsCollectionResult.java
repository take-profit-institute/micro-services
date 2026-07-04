package org.profit.candle.news.collector;

public record NewsCollectionResult(
        int targetCount,
        int successCount,
        int failCount
) {
}

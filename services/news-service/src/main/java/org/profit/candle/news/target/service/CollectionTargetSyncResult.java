package org.profit.candle.news.target.service;

public record CollectionTargetSyncResult(
        int syncedCount,
        int pageCount
) {
}

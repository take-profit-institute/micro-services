package org.profit.candle.batch.portfolio.eod.model;

import java.util.List;

public record SnapshotTarget(
        String userId,
        List<Holding> holdings
) {
    public SnapshotTarget {
        holdings = List.copyOf(holdings);
    }

    public record Holding(
            String symbol,
            long quantity,
            long averagePrice
    ) {
    }

    public record Page(
            List<SnapshotTarget> targets,
            String nextPageToken
    ) {
        public Page {
            targets = List.copyOf(targets);
        }
    }
}

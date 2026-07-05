package org.profit.candle.ranking.ranking.client;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioSnapshotItem(
        UUID userId,
        long totalAsset,
        BigDecimal cumulativeReturnRate) {}

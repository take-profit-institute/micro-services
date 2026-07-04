package org.profit.candle.ranking.ranking.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DailyRankingRow(
        int position,
        UUID userId,
        long totalAsset,
        BigDecimal profitRate,
        int tradeCount) {}

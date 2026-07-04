package org.profit.candle.ranking.ranking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RankingResult(
        int position,
        UUID userId,
        String nickname,
        long totalAsset,
        BigDecimal profitRate,
        int tradeCount,
        LocalDate rankingDate) {}

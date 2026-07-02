package org.profit.candle.ranking.ranking.dto;

import java.time.LocalDate;

public record DailyRankingResult(LocalDate rankingDate, int rankedUserCount) {}

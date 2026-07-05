package org.profit.candle.learning.state.dto;

import java.util.List;

public record LearningStatsResult(
        long totalContents,
        long completedContents,
        int overallProgressPct,
        List<CategoryProgressResult> categoryStats
) {
    public record CategoryProgressResult(
            String category,
            int total,
            int completed,
            int progressPct
    ) {}
}
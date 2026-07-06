package org.profit.candle.learning.content.dto;

import org.profit.candle.learning.content.entity.ContentLevel;

public record CreateContentCommand(
        String title,
        String description,
        String category,
        ContentLevel level,
        String body,
        short durationMin,
        long xpReward,
        String[] keywords,
        boolean published
) {}
package org.profit.candle.learning.content.dto;

import org.profit.candle.learning.content.entity.ContentLevel;

import java.util.UUID;

public record UpdateContentCommand(
        UUID contentId,
        String title,
        String description,
        String category,
        ContentLevel level,
        String body,
        Short durationMin,
        Long xpReward,
        String[] keywords,
        Boolean published
) {}
package org.profit.candle.learning.content.dto;

import org.profit.candle.learning.content.entity.Content;
import org.profit.candle.learning.content.entity.ContentLevel;

import java.time.Instant;
import java.util.UUID;

public record ContentResult(
        UUID id,
        String title,
        String description,
        String category,
        ContentLevel level,
        String body,
        short durationMin,
        long xpReward,
        String[] keywords,
        boolean published,
        long readCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ContentResult from(Content content) {
        return new ContentResult(
                content.getId(),
                content.getTitle(),
                content.getDescription(),
                content.getCategory(),
                content.getLevel(),
                content.getBody(),
                content.getDurationMin(),
                content.getXpReward(),
                content.getKeywords(),
                content.published(),
                content.getReadCount(),
                content.getCreatedAt(),
                content.getUpdatedAt()
        );
    }
}
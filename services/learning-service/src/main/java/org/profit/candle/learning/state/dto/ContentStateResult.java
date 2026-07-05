package org.profit.candle.learning.state.dto;

import org.profit.candle.learning.state.entity.UserContentState;

import java.time.Instant;
import java.util.UUID;

public record ContentStateResult(
        UUID id,
        UUID contentId,
        short progressPct,
        boolean completed,
        boolean favorite,
        Instant completedAt,
        Instant lastReadAt
) {
    public static ContentStateResult from(UserContentState state) {
        return new ContentStateResult(
                state.getId(),
                state.getContent().getId(),
                state.getProgressPct(),
                state.completed(),
                state.favorite(),
                state.getCompletedAt(),
                state.getLastReadAt()
        );
    }
}
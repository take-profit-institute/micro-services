package org.profit.candle.learning.state.service;

import org.profit.candle.learning.state.dto.ContentStateResult;
import org.profit.candle.learning.state.dto.LearningStatsResult;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserContentStateService {

    Optional<ContentStateResult> findState(UUID userId, UUID contentId);

    ContentStateResult updateProgress(UUID userId, UUID contentId, short progressPct);

    ContentStateResult completeContent(UUID userId, UUID contentId);

    ContentStateResult toggleFavorite(UUID userId, UUID contentId);

    Page<ContentStateResult> listFavorites(UUID userId, int page, int size);

    LearningStatsResult getUserStats(UUID userId);

    List<UUID> getRecommendedContentIds(UUID userId, int limit);
}
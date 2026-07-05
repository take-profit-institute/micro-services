package org.profit.candle.learning.state.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.content.entity.Content;
import org.profit.candle.learning.content.repository.ContentRepository;
import org.profit.candle.learning.event.OutboxWriter;
import org.profit.candle.learning.exception.LearningErrorCode;
import org.profit.candle.learning.exception.LearningException;
import org.profit.candle.learning.state.dto.ContentStateResult;
import org.profit.candle.learning.state.dto.LearningStatsResult;
import org.profit.candle.learning.state.dto.LearningStatsResult.CategoryProgressResult;
import org.profit.candle.learning.state.entity.UserContentState;
import org.profit.candle.learning.state.repository.UserContentStateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultUserContentStateService implements UserContentStateService {

    private final UserContentStateRepository stateRepository;
    private final ContentRepository contentRepository;
    private final OutboxWriter outboxWriter;

    @Override
    public Optional<ContentStateResult> findState(UUID userId, UUID contentId) {
        return stateRepository.findByUserIdAndContentId(userId, contentId)
                .map(ContentStateResult::from);
    }

    @Override
    @Transactional
    public ContentStateResult updateProgress(UUID userId, UUID contentId, short progressPct) {
        UserContentState state = getOrCreate(userId, contentId);
        boolean wasCompleted = state.completed();
        state.updateProgress(progressPct);

        // 진도 100% 도달로 새로 완료된 경우 이벤트 발행
        if (!wasCompleted && state.completed()) {
            outboxWriter.recordLearningCompleted(userId, contentId);
        }
        return ContentStateResult.from(state);
    }

    @Override
    @Transactional
    public ContentStateResult completeContent(UUID userId, UUID contentId) {
        UserContentState state = getOrCreate(userId, contentId);
        if (!state.completed()) {
            state.markCompleted();
            outboxWriter.recordLearningCompleted(userId, contentId);
        }
        return ContentStateResult.from(state);
    }

    @Override
    @Transactional
    public ContentStateResult toggleFavorite(UUID userId, UUID contentId) {
        UserContentState state = getOrCreate(userId, contentId);
        state.toggleFavorite();
        return ContentStateResult.from(state);
    }

    @Override
    public Page<ContentStateResult> listFavorites(UUID userId, int page, int size) {
        return stateRepository.findFavorites(userId, PageRequest.of(page, size))
                .map(ContentStateResult::from);
    }

    @Override
    public LearningStatsResult getUserStats(UUID userId) {
        long totalContents = contentRepository.countByPublishedTrue();
        long completedContents = stateRepository.countByUserIdAndCompletedTrue(userId);
        int overallPct = totalContents == 0 ? 0 : (int) (completedContents * 100 / totalContents);

        List<Object[]> categoryRaw = stateRepository.countCompletedByCategory(userId);
        List<CategoryProgressResult> categoryStats = categoryRaw.stream()
                .map(row -> {
                    String category = (String) row[0];
                    long completed = (Long) row[1];
                    long total = contentRepository.countByCategoryAndPublishedTrue(category);
                    int pct = total == 0 ? 0 : (int) (completed * 100 / total);
                    return new CategoryProgressResult(category, (int) total, (int) completed, pct);
                }).toList();

        return new LearningStatsResult(totalContents, completedContents, overallPct, categoryStats);
    }

    @Override
    public List<UUID> getRecommendedContentIds(UUID userId, int limit) {
        List<UUID> unread = stateRepository.findUnreadContentIds(userId);
        if (unread.size() <= limit) return unread;
        List<UUID> shuffled = new ArrayList<>(unread);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, limit);
    }

    private UserContentState getOrCreate(UUID userId, UUID contentId) {
        return stateRepository.findByUserIdAndContentId(userId, contentId)
                .orElseGet(() -> {
                    Content content = contentRepository.findById(contentId)
                            .orElseThrow(() -> new LearningException(LearningErrorCode.CONTENT_NOT_FOUND));
                    return stateRepository.save(UserContentState.create(userId, content));
                });
    }
}
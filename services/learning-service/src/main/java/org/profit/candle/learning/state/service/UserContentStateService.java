package org.profit.candle.learning.state.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.content.entity.Content;
import org.profit.candle.learning.content.service.ContentService;
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
public class UserContentStateService {

    private final UserContentStateRepository stateRepository;
    private final ContentService contentService;

    /**
     * 사용자-콘텐츠 상태 조회. 없으면 Optional.empty.
     */
    public Optional<UserContentState> findState(UUID userId, UUID contentId) {
        return stateRepository.findByUserIdAndContentId(userId, contentId);
    }

    /**
     * 상태가 없으면 새로 생성해서 반환 (getOrCreate 패턴).
     * 최초 콘텐츠 열람 시 호출됨.
     */
    @Transactional
    public UserContentState getOrCreate(UUID userId, UUID contentId) {
        return stateRepository.findByUserIdAndContentId(userId, contentId)
                .orElseGet(() -> {
                    Content content = contentService.getById(contentId);
                    return stateRepository.save(UserContentState.create(userId, content));
                });
    }

    @Transactional
    public UserContentState updateProgress(UUID userId, UUID contentId, short progressPct) {
        UserContentState state = getOrCreate(userId, contentId);
        state.updateProgress(progressPct);
        return state;
    }

    @Transactional
    public UserContentState completeContent(UUID userId, UUID contentId) {
        UserContentState state = getOrCreate(userId, contentId);
        state.markCompleted();
        return state;
    }

    @Transactional
    public UserContentState toggleFavorite(UUID userId, UUID contentId) {
        UserContentState state = getOrCreate(userId, contentId);
        state.toggleFavorite();
        return state;
    }

    public Page<UserContentState> listFavorites(UUID userId, int page, int size) {
        return stateRepository.findFavorites(userId, PageRequest.of(page, size));
    }

    /**
     * 대시보드용 학습 현황.
     * 전체 진도율 = (완료 수 / 전체 공개 콘텐츠 수) * 100
     */
    public LearningStats getUserStats(UUID userId) {
        long totalContents = contentService.countAll();
        long completedContents = stateRepository.countByUserIdAndCompletedTrue(userId);

        int overallPct = totalContents == 0 ? 0
                : (int) (completedContents * 100 / totalContents);

        // 카테고리별 완료 수
        List<Object[]> categoryRaw = stateRepository.countCompletedByCategory(userId);
        Map<String, Long> completedByCategory = new HashMap<>();
        for (Object[] row : categoryRaw) {
            completedByCategory.put((String) row[0], (Long) row[1]);
        }

        return new LearningStats(totalContents, completedContents, overallPct, completedByCategory);
    }

    /**
     * 추천 콘텐츠: 아직 안 본 콘텐츠 중 limit개 반환.
     */
    public List<UUID> getRecommendedContentIds(UUID userId, int limit) {
        List<UUID> unread = stateRepository.findUnreadContentIds(userId);
        if (unread.size() <= limit) return unread;
        Collections.shuffle(unread);
        return unread.subList(0, limit);
    }

    // 대시보드 응답 DTO
    public record LearningStats(
            long totalContents,
            long completedContents,
            int overallProgressPct,
            Map<String, Long> completedByCategory
    ) {}
}
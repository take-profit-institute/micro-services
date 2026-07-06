package org.profit.candle.learning.state.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.learning.content.entity.Content;
import org.profit.candle.learning.content.entity.ContentLevel;
import org.profit.candle.learning.content.repository.ContentRepository;
import org.profit.candle.learning.event.OutboxWriter;
import org.profit.candle.learning.state.dto.ContentStateResult;
import org.profit.candle.learning.state.entity.UserContentState;
import org.profit.candle.learning.state.repository.UserContentStateRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultUserContentStateServiceTest {

    @InjectMocks
    private DefaultUserContentStateService sut;

    @Mock
    private UserContentStateRepository stateRepository;
    @Mock
    private ContentRepository contentRepository;
    @Mock
    private OutboxWriter outboxWriter;

    private UUID userId;
    private UUID contentId;
    private Content sampleContent;
    private UserContentState sampleState;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        contentId = UUID.randomUUID();
        sampleContent = Content.create(
                "테스트 콘텐츠", "설명", "기술적분석",
                ContentLevel.BEGINNER, "본문", (short) 5, 100L,
                new String[]{"테스트"}, true);
        sampleState = UserContentState.create(userId, sampleContent);
    }

    @Nested
    @DisplayName("updateProgress")
    class UpdateProgress {

        @Test
        @DisplayName("진도 50% 업데이트 — 완료 이벤트 미발행")
        void partialProgress() {
            given(stateRepository.findByUserIdAndContentId(userId, contentId))
                    .willReturn(Optional.of(sampleState));

            ContentStateResult result = sut.updateProgress(userId, contentId, (short) 50);

            assertThat(result.progressPct()).isEqualTo((short) 50);
            assertThat(result.completed()).isFalse();
            then(outboxWriter).should(never()).recordLearningCompleted(any(), any());
        }

        @Test
        @DisplayName("진도 100% → 자동 완료 + LearningCompleted 이벤트 발행")
        void completeViaProgress() {
            given(stateRepository.findByUserIdAndContentId(userId, contentId))
                    .willReturn(Optional.of(sampleState));

            ContentStateResult result = sut.updateProgress(userId, contentId, (short) 100);

            assertThat(result.progressPct()).isEqualTo((short) 100);
            assertThat(result.completed()).isTrue();
            then(outboxWriter).should().recordLearningCompleted(userId, contentId);
        }

        @Test
        @DisplayName("이미 완료된 콘텐츠 100% 재전송 — 이벤트 중복 발행 안 됨")
        void alreadyCompleted() {
            sampleState.markCompleted(); // 이미 완료
            given(stateRepository.findByUserIdAndContentId(userId, contentId))
                    .willReturn(Optional.of(sampleState));

            sut.updateProgress(userId, contentId, (short) 100);

            then(outboxWriter).should(never()).recordLearningCompleted(any(), any());
        }
    }

    @Nested
    @DisplayName("completeContent")
    class CompleteContent {

        @Test
        @DisplayName("미완료 콘텐츠 완료 처리 + 이벤트 발행")
        void success() {
            given(stateRepository.findByUserIdAndContentId(userId, contentId))
                    .willReturn(Optional.of(sampleState));

            ContentStateResult result = sut.completeContent(userId, contentId);

            assertThat(result.completed()).isTrue();
            assertThat(result.completedAt()).isNotNull();
            then(outboxWriter).should().recordLearningCompleted(userId, contentId);
        }

        @Test
        @DisplayName("이미 완료된 콘텐츠 — 이벤트 중복 발행 안 됨")
        void idempotent() {
            sampleState.markCompleted();
            given(stateRepository.findByUserIdAndContentId(userId, contentId))
                    .willReturn(Optional.of(sampleState));

            sut.completeContent(userId, contentId);

            then(outboxWriter).should(never()).recordLearningCompleted(any(), any());
        }
    }

    @Nested
    @DisplayName("toggleFavorite")
    class ToggleFavorite {

        @Test
        @DisplayName("즐겨찾기 토글 — false → true")
        void toggle() {
            given(stateRepository.findByUserIdAndContentId(userId, contentId))
                    .willReturn(Optional.of(sampleState));

            ContentStateResult result = sut.toggleFavorite(userId, contentId);

            assertThat(result.favorite()).isTrue();
        }

        @Test
        @DisplayName("즐겨찾기 재토글 — true → false")
        void toggleBack() {
            sampleState.toggleFavorite(); // true로 만듦
            given(stateRepository.findByUserIdAndContentId(userId, contentId))
                    .willReturn(Optional.of(sampleState));

            ContentStateResult result = sut.toggleFavorite(userId, contentId);

            assertThat(result.favorite()).isFalse();
        }
    }

    @Nested
    @DisplayName("getOrCreate")
    class GetOrCreate {

        @Test
        @DisplayName("기존 상태 없으면 새로 생성")
        void createNew() {
            given(stateRepository.findByUserIdAndContentId(userId, contentId))
                    .willReturn(Optional.empty());
            given(contentRepository.findById(contentId))
                    .willReturn(Optional.of(sampleContent));
            given(stateRepository.save(any(UserContentState.class)))
                    .willReturn(sampleState);

            ContentStateResult result = sut.updateProgress(userId, contentId, (short) 10);

            assertThat(result.progressPct()).isEqualTo((short) 10);
            then(stateRepository).should().save(any(UserContentState.class));
        }
    }
}
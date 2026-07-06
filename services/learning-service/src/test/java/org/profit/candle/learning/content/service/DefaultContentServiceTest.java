package org.profit.candle.learning.content.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.learning.content.dto.ContentResult;
import org.profit.candle.learning.content.dto.CreateContentCommand;
import org.profit.candle.learning.content.dto.UpdateContentCommand;
import org.profit.candle.learning.content.entity.Content;
import org.profit.candle.learning.content.entity.ContentLevel;
import org.profit.candle.learning.content.repository.ContentRepository;
import org.profit.candle.learning.exception.LearningException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultContentServiceTest {

    @InjectMocks
    private DefaultContentService sut;

    @Mock
    private ContentRepository contentRepository;

    private Content sampleContent;
    private UUID contentId;

    @BeforeEach
    void setUp() {
        contentId = UUID.randomUUID();
        sampleContent = Content.create(
                "캔들스틱 차트 읽는 법", "양봉과 음봉의 의미", "기술적분석",
                ContentLevel.BEGINNER, "본문 내용", (short) 5, 100L,
                new String[]{"캔들", "차트"}, true);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("정상 생성 시 ContentResult 반환")
        void success() {
            given(contentRepository.save(any(Content.class))).willReturn(sampleContent);

            CreateContentCommand cmd = new CreateContentCommand(
                    "캔들스틱 차트 읽는 법", "양봉과 음봉의 의미", "기술적분석",
                    ContentLevel.BEGINNER, "본문 내용", (short) 5, 100L,
                    new String[]{"캔들", "차트"}, true);

            ContentResult result = sut.create(cmd);

            assertThat(result.title()).isEqualTo("캔들스틱 차트 읽는 법");
            assertThat(result.level()).isEqualTo(ContentLevel.BEGINNER);
            assertThat(result.published()).isTrue();
            then(contentRepository).should().save(any(Content.class));
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("존재하는 콘텐츠 조회 성공")
        void success() {
            given(contentRepository.findById(contentId)).willReturn(Optional.of(sampleContent));

            ContentResult result = sut.getById(contentId);

            assertThat(result.title()).isEqualTo("캔들스틱 차트 읽는 법");
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 조회 시 LearningException")
        void notFound() {
            given(contentRepository.findById(contentId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getById(contentId))
                    .isInstanceOf(LearningException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("부분 수정 — null 필드는 변경 안 됨")
        void partialUpdate() {
            given(contentRepository.findById(contentId)).willReturn(Optional.of(sampleContent));

            UpdateContentCommand cmd = new UpdateContentCommand(
                    contentId, "수정된 제목", null, null,
                    null, null, null, null, null, null);

            ContentResult result = sut.update(cmd);

            assertThat(result.title()).isEqualTo("수정된 제목");
            assertThat(result.category()).isEqualTo("기술적분석"); // 변경 안 됨
        }
    }

    @Nested
    @DisplayName("softDelete")
    class SoftDelete {

        @Test
        @DisplayName("soft delete 후 deletedAt 세팅됨")
        void success() {
            given(contentRepository.findById(contentId)).willReturn(Optional.of(sampleContent));

            sut.softDelete(contentId);

            assertThat(sampleContent.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 삭제 시 예외")
        void notFound() {
            given(contentRepository.findById(contentId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sut.softDelete(contentId))
                    .isInstanceOf(LearningException.class);
        }
    }
}
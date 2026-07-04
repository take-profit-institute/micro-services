package org.profit.candle.learning.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.content.entity.Content;
import org.profit.candle.learning.content.entity.ContentLevel;
import org.profit.candle.learning.content.service.ContentService;
import org.profit.candle.learning.exception.LearningErrorCode;
import org.profit.candle.learning.exception.LearningException;
import org.profit.candle.learning.proto.*;
import org.profit.candle.learning.state.entity.UserContentState;
import org.profit.candle.learning.state.service.UserContentStateService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LearningGrpcService extends LearningServiceGrpc.LearningServiceImplBase {

    private final ContentService contentService;
    private final UserContentStateService stateService;

    // ─── 콘텐츠 CRUD (관리자) ───

    @Override
    public void createContent(CreateContentRequest req, StreamObserver<ContentResponse> observer) {
        try {
            Content content = contentService.create(
                    req.getTitle(), req.getDescription(), req.getCategory(),
                    toContentLevel(req.getLevel()), req.getBody(),
                    (short) req.getDurationMin(), req.getXpReward(),
                    req.getKeywordsList().toArray(String[]::new), req.getIsPublished());
            observer.onNext(toContentResponse(content));
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void updateContent(UpdateContentRequest req, StreamObserver<ContentResponse> observer) {
        try {
            Content content = contentService.update(
                    UUID.fromString(req.getContentId()),
                    req.hasTitle() ? req.getTitle() : null,
                    req.hasDescription() ? req.getDescription() : null,
                    req.hasCategory() ? req.getCategory() : null,
                    req.hasLevel() ? toContentLevel(req.getLevel()) : null,
                    req.hasBody() ? req.getBody() : null,
                    req.hasDurationMin() ? (short) req.getDurationMin() : null,
                    req.hasXpReward() ? req.getXpReward() : null,
                    req.getKeywordsList().isEmpty() ? null : req.getKeywordsList().toArray(String[]::new),
                    req.hasIsPublished() ? req.getIsPublished() : null);
            observer.onNext(toContentResponse(content));
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void deleteContent(DeleteContentRequest req, StreamObserver<DeleteContentResponse> observer) {
        try {
            contentService.softDelete(UUID.fromString(req.getContentId()));
            observer.onNext(DeleteContentResponse.newBuilder().setSuccess(true).build());
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    // ─── 콘텐츠 조회 ───

    @Override
    public void getContent(GetContentRequest req, StreamObserver<ContentDetailResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            UUID contentId = UUID.fromString(req.getContentId());
            Content content = contentService.getAndIncrementReadCount(contentId);

            ContentDetailResponse.Builder builder = toContentDetailBuilder(content);
            stateService.findState(userId, contentId)
                    .ifPresent(state -> builder.setUserState(toStateResponse(state)));

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void listContents(ListContentsRequest req, StreamObserver<ListContentsResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            String category = req.hasCategory() ? req.getCategory() : null;
            ContentLevel level = req.hasLevel() ? toContentLevel(req.getLevel()) : null;
            String sortBy = req.getSortBy().name().replace("CONTENT_SORT_BY_", "");

            Page<Content> page = contentService.list(category, level, sortBy, req.getPage(), req.getSize());
            observer.onNext(toListResponse(page, userId));
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void searchContents(SearchContentsRequest req, StreamObserver<ListContentsResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            String category = req.hasCategory() ? req.getCategory() : null;
            ContentLevel level = req.hasLevel() ? toContentLevel(req.getLevel()) : null;

            Page<Content> page = contentService.search(req.getQuery(), category, level, req.getPage(), req.getSize());
            observer.onNext(toListResponse(page, userId));
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void getRecommendedContents(GetRecommendedContentsRequest req, StreamObserver<ListContentsResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            int limit = req.getLimit() > 0 ? req.getLimit() : 4;

            List<ContentWithStateResponse> items = stateService.getRecommendedContentIds(userId, limit).stream()
                    .map(id -> {
                        Content c = contentService.getById(id);
                        ContentWithStateResponse.Builder b = ContentWithStateResponse.newBuilder()
                                .setContent(toContentResponse(c));
                        stateService.findState(userId, id)
                                .ifPresent(s -> b.setUserState(toStateResponse(s)));
                        return b.build();
                    }).toList();

            observer.onNext(ListContentsResponse.newBuilder()
                    .addAllContents(items)
                    .setTotalCount(items.size())
                    .setPage(0).setSize(limit).build());
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    // ─── 사용자 학습 상태 ───

    @Override
    public void updateProgress(UpdateProgressRequest req, StreamObserver<UserContentStateResponse> observer) {
        try {
            UserContentState state = stateService.updateProgress(
                    UUID.fromString(req.getUserId()), UUID.fromString(req.getContentId()),
                    (short) req.getProgressPct());
            observer.onNext(toStateResponse(state));
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void completeContent(CompleteContentRequest req, StreamObserver<UserContentStateResponse> observer) {
        try {
            UserContentState state = stateService.completeContent(
                    UUID.fromString(req.getUserId()), UUID.fromString(req.getContentId()));
            observer.onNext(toStateResponse(state));
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void toggleFavorite(ToggleFavoriteRequest req, StreamObserver<UserContentStateResponse> observer) {
        try {
            UserContentState state = stateService.toggleFavorite(
                    UUID.fromString(req.getUserId()), UUID.fromString(req.getContentId()));
            observer.onNext(toStateResponse(state));
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    // ─── 대시보드 ───

    @Override
    public void getUserLearningStats(GetUserLearningStatsRequest req, StreamObserver<UserLearningStatsResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            UserContentStateService.LearningStats stats = stateService.getUserStats(userId);

            UserLearningStatsResponse.Builder builder = UserLearningStatsResponse.newBuilder()
                    .setTotalContents((int) stats.totalContents())
                    .setCompletedContents((int) stats.completedContents())
                    .setOverallProgressPct(stats.overallProgressPct());

            stats.completedByCategory().forEach((category, completed) -> {
                long total = contentService.countByCategory(category);
                int pct = total == 0 ? 0 : (int) (completed * 100 / total);
                builder.addCategoryStats(CategoryProgress.newBuilder()
                        .setCategory(category).setTotal((int) total)
                        .setCompleted(completed.intValue()).setProgressPct(pct).build());
            });

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void listFavorites(ListFavoritesRequest req, StreamObserver<ListContentsResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            Page<UserContentState> page = stateService.listFavorites(userId, req.getPage(), req.getSize());

            List<ContentWithStateResponse> items = page.getContent().stream()
                    .map(state -> ContentWithStateResponse.newBuilder()
                            .setContent(toContentResponse(state.getContent()))
                            .setUserState(toStateResponse(state)).build())
                    .toList();

            observer.onNext(ListContentsResponse.newBuilder()
                    .addAllContents(items)
                    .setTotalCount((int) page.getTotalElements())
                    .setPage(req.getPage()).setSize(req.getSize()).build());
            observer.onCompleted();
        } catch (LearningException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    // ─── 에러 매핑 ───

    private Status toGrpcStatus(LearningException e) {
        String code = e.errorCode().code();
        if (code.equals(LearningErrorCode.CONTENT_NOT_FOUND.code())) {
            return Status.NOT_FOUND.withDescription(code);
        }
        if (code.equals(LearningErrorCode.INVALID_PROGRESS.code())) {
            return Status.INVALID_ARGUMENT.withDescription(code);
        }
        return Status.INTERNAL.withDescription(code);
    }

    // ─── 매핑 헬퍼 ───

    private ContentLevel toContentLevel(org.profit.candle.learning.proto.ContentLevel protoLevel) {
        return switch (protoLevel) {
            case CONTENT_LEVEL_BEGINNER -> ContentLevel.BEGINNER;
            case CONTENT_LEVEL_INTERMEDIATE -> ContentLevel.INTERMEDIATE;
            case CONTENT_LEVEL_ADVANCED -> ContentLevel.ADVANCED;
            default -> throw new IllegalArgumentException("Unknown level: " + protoLevel);
        };
    }

    private org.profit.candle.learning.proto.ContentLevel toProtoLevel(ContentLevel level) {
        return switch (level) {
            case BEGINNER -> org.profit.candle.learning.proto.ContentLevel.CONTENT_LEVEL_BEGINNER;
            case INTERMEDIATE -> org.profit.candle.learning.proto.ContentLevel.CONTENT_LEVEL_INTERMEDIATE;
            case ADVANCED -> org.profit.candle.learning.proto.ContentLevel.CONTENT_LEVEL_ADVANCED;
        };
    }

    private ContentResponse toContentResponse(Content c) {
        ContentResponse.Builder builder = ContentResponse.newBuilder()
                .setId(c.getId().toString())
                .setTitle(c.getTitle())
                .setCategory(c.getCategory())
                .setLevel(toProtoLevel(c.getLevel()))
                .setDurationMin(c.getDurationMin())
                .setXpReward(c.getXpReward())
                .setIsPublished(c.isPublished())
                .setReadCount(c.getReadCount())
                .setCreatedAt(toTimestamp(c.getCreatedAt()))
                .setUpdatedAt(toTimestamp(c.getUpdatedAt()));
        if (c.getDescription() != null) builder.setDescription(c.getDescription());
        if (c.getKeywords() != null) builder.addAllKeywords(List.of(c.getKeywords()));
        return builder.build();
    }

    private ContentDetailResponse.Builder toContentDetailBuilder(Content c) {
        ContentDetailResponse.Builder builder = ContentDetailResponse.newBuilder()
                .setId(c.getId().toString())
                .setTitle(c.getTitle())
                .setCategory(c.getCategory())
                .setLevel(toProtoLevel(c.getLevel()))
                .setDurationMin(c.getDurationMin())
                .setXpReward(c.getXpReward())
                .setIsPublished(c.isPublished())
                .setReadCount(c.getReadCount())
                .setCreatedAt(toTimestamp(c.getCreatedAt()))
                .setUpdatedAt(toTimestamp(c.getUpdatedAt()));
        if (c.getDescription() != null) builder.setDescription(c.getDescription());
        if (c.getBody() != null) builder.setBody(c.getBody());
        if (c.getKeywords() != null) builder.addAllKeywords(List.of(c.getKeywords()));
        return builder;
    }

    private UserContentStateResponse toStateResponse(UserContentState s) {
        UserContentStateResponse.Builder builder = UserContentStateResponse.newBuilder()
                .setId(s.getId().toString())
                .setContentId(s.getContent().getId().toString())
                .setProgressPct(s.getProgressPct())
                .setIsCompleted(s.isCompleted())
                .setIsFavorite(s.isFavorite());
        if (s.getCompletedAt() != null) builder.setCompletedAt(toTimestamp(s.getCompletedAt()));
        if (s.getLastReadAt() != null) builder.setLastReadAt(toTimestamp(s.getLastReadAt()));
        return builder.build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private ListContentsResponse toListResponse(Page<Content> page, UUID userId) {
        List<ContentWithStateResponse> items = page.getContent().stream()
                .map(c -> {
                    ContentWithStateResponse.Builder b = ContentWithStateResponse.newBuilder()
                            .setContent(toContentResponse(c));
                    stateService.findState(userId, c.getId())
                            .ifPresent(s -> b.setUserState(toStateResponse(s)));
                    return b.build();
                }).toList();

        return ListContentsResponse.newBuilder()
                .addAllContents(items)
                .setTotalCount((int) page.getTotalElements())
                .setPage(page.getNumber())
                .setSize(page.getSize())
                .build();
    }
}
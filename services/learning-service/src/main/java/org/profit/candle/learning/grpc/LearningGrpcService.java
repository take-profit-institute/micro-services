package org.profit.candle.learning.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.learning.content.dto.ContentResult;
import org.profit.candle.learning.content.dto.CreateContentCommand;
import org.profit.candle.learning.content.dto.UpdateContentCommand;
import org.profit.candle.learning.content.entity.ContentLevel;
import org.profit.candle.learning.content.service.ContentService;
import org.profit.candle.learning.exception.LearningErrorCode;
import org.profit.candle.learning.exception.LearningException;
import org.profit.candle.learning.idempotency.service.IdempotencyExecutor;
import org.profit.candle.learning.proto.*;
import org.profit.candle.learning.state.dto.ContentStateResult;
import org.profit.candle.learning.state.dto.LearningStatsResult;
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
    private final IdempotencyExecutor idempotencyExecutor;

    // ─── 콘텐츠 CRUD (관리자) ───

    @Override
    public void createContent(CreateContentRequest req, StreamObserver<ContentResponse> observer) {
        try {
            // 관리자 RPC — userId 대신 고정값 사용. 추후 관리자 인증 연동 시 교체.
            UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000000");
            String requestHash = hashOf(req.getTitle(), req.getCategory(), req.getLevel().name());

            ContentResult result = idempotencyExecutor.execute(
                    adminId, "CreateContent", req.getIdempotencyKey(),
                    requestHash, ContentResult.class,
                    () -> contentService.create(toCreateCommand(req)));

            observer.onNext(toContentResponse(result));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void updateContent(UpdateContentRequest req, StreamObserver<ContentResponse> observer) {
        try {
            UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000000");
            String requestHash = hashOf(req.getContentId(), req.getIdempotencyKey());

            ContentResult result = idempotencyExecutor.execute(
                    adminId, "UpdateContent", req.getIdempotencyKey(),
                    requestHash, ContentResult.class,
                    () -> contentService.update(toUpdateCommand(req)));

            observer.onNext(toContentResponse(result));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void deleteContent(DeleteContentRequest req, StreamObserver<DeleteContentResponse> observer) {
        try {
            UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000000");
            String requestHash = hashOf(req.getContentId());

            idempotencyExecutor.execute(
                    adminId, "DeleteContent", req.getIdempotencyKey(),
                    requestHash, Boolean.class,
                    () -> { contentService.softDelete(UUID.fromString(req.getContentId())); return true; });

            observer.onNext(DeleteContentResponse.newBuilder().setSuccess(true).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void listAdminContents(ListAdminContentsRequest req,
                                  StreamObserver<ListAdminContentsResponse> observer) {
        try {
            Boolean published = req.hasPublished() ? req.getPublished() : null;
            int page = Math.max(req.getPage(), 0);
            int size = req.getSize() > 0 ? Math.min(req.getSize(), 100) : 20;
            Page<ContentResult> contents = contentService.adminList(published, page, size);

            ListAdminContentsResponse.Builder response = ListAdminContentsResponse.newBuilder()
                    .setTotalCount((int) contents.getTotalElements())
                    .setPage(contents.getNumber())
                    .setSize(contents.getSize());
            contents.getContent().forEach(c -> response.addContents(toContentResponse(c)));

            observer.onNext(response.build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    // ─── 콘텐츠 조회 ───

    @Override
    public void getContent(GetContentRequest req, StreamObserver<ContentDetailResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            UUID contentId = UUID.fromString(req.getContentId());
            ContentResult content = contentService.getAndIncrementReadCount(contentId);

            ContentDetailResponse.Builder builder = toContentDetailBuilder(content);
            stateService.findState(userId, contentId)
                    .ifPresent(state -> builder.setUserState(toStateResponse(state)));

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
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

            Page<ContentResult> page = contentService.list(category, level, sortBy, req.getPage(), req.getSize());
            observer.onNext(toListResponse(page, userId));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void searchContents(SearchContentsRequest req, StreamObserver<ListContentsResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            String category = req.hasCategory() ? req.getCategory() : null;
            ContentLevel level = req.hasLevel() ? toContentLevel(req.getLevel()) : null;

            Page<ContentResult> page = contentService.search(req.getQuery(), category, level, req.getPage(), req.getSize());
            observer.onNext(toListResponse(page, userId));
            observer.onCompleted();
        } catch (Exception e) {
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
                        ContentResult c = contentService.getById(id);
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
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    // ─── 사용자 액션 (idempotency 적용) ───

    @Override
    public void updateProgress(UpdateProgressRequest req, StreamObserver<UserContentStateResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            String requestHash = hashOf(req.getUserId(), req.getContentId(), String.valueOf(req.getProgressPct()));

            ContentStateResult result = idempotencyExecutor.execute(
                    userId, "UpdateProgress", req.getIdempotencyKey(),
                    requestHash, ContentStateResult.class,
                    () -> stateService.updateProgress(userId, UUID.fromString(req.getContentId()), (short) req.getProgressPct()));

            observer.onNext(toStateResponse(result));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void completeContent(CompleteContentRequest req, StreamObserver<UserContentStateResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            String requestHash = hashOf(req.getUserId(), req.getContentId());

            ContentStateResult result = idempotencyExecutor.execute(
                    userId, "CompleteContent", req.getIdempotencyKey(),
                    requestHash, ContentStateResult.class,
                    () -> stateService.completeContent(userId, UUID.fromString(req.getContentId())));

            observer.onNext(toStateResponse(result));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void toggleFavorite(ToggleFavoriteRequest req, StreamObserver<UserContentStateResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            String requestHash = hashOf(req.getUserId(), req.getContentId());

            ContentStateResult result = idempotencyExecutor.execute(
                    userId, "ToggleFavorite", req.getIdempotencyKey(),
                    requestHash, ContentStateResult.class,
                    () -> stateService.toggleFavorite(userId, UUID.fromString(req.getContentId())));

            observer.onNext(toStateResponse(result));
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    // ─── 대시보드 ───

    @Override
    public void getUserLearningStats(GetUserLearningStatsRequest req, StreamObserver<UserLearningStatsResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            LearningStatsResult stats = stateService.getUserStats(userId);

            UserLearningStatsResponse.Builder builder = UserLearningStatsResponse.newBuilder()
                    .setTotalContents((int) stats.totalContents())
                    .setCompletedContents((int) stats.completedContents())
                    .setOverallProgressPct(stats.overallProgressPct());

            stats.categoryStats().forEach(cat ->
                    builder.addCategoryStats(CategoryProgress.newBuilder()
                            .setCategory(cat.category())
                            .setTotal(cat.total())
                            .setCompleted(cat.completed())
                            .setProgressPct(cat.progressPct()).build()));

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void listFavorites(ListFavoritesRequest req, StreamObserver<ListContentsResponse> observer) {
        try {
            UUID userId = UUID.fromString(req.getUserId());
            Page<ContentStateResult> page = stateService.listFavorites(userId, req.getPage(), req.getSize());

            List<ContentWithStateResponse> items = page.getContent().stream()
                    .map(state -> {
                        ContentResult c = contentService.getById(state.contentId());
                        return ContentWithStateResponse.newBuilder()
                                .setContent(toContentResponse(c))
                                .setUserState(toStateResponse(state)).build();
                    }).toList();

            observer.onNext(ListContentsResponse.newBuilder()
                    .addAllContents(items)
                    .setTotalCount((int) page.getTotalElements())
                    .setPage(req.getPage()).setSize(req.getSize()).build());
            observer.onCompleted();
        } catch (Exception e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    // ─── 에러 매핑 ───

    private Status toGrpcStatus(Exception e) {
        if (e instanceof LearningException le) {
            String code = le.errorCode().code();
            if (code.equals(LearningErrorCode.CONTENT_NOT_FOUND.code())) {
                return Status.NOT_FOUND.withDescription(code);
            }
            if (code.equals(LearningErrorCode.IDEMPOTENCY_KEY_REQUIRED.code())) {
                return Status.INVALID_ARGUMENT.withDescription(code);
            }
            if (code.equals(LearningErrorCode.IDEMPOTENCY_REQUEST_MISMATCH.code())) {
                return Status.ALREADY_EXISTS.withDescription(code);
            }
            return Status.INTERNAL.withDescription(code);
        }
        if (e instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
        }
        return Status.INTERNAL.withDescription("Internal server error");
    }

    // ─── 매핑 헬퍼 ───

    private CreateContentCommand toCreateCommand(CreateContentRequest req) {
        return new CreateContentCommand(
                req.getTitle(), req.getDescription(), req.getCategory(),
                toContentLevel(req.getLevel()), req.getBody(),
                (short) req.getDurationMin(), req.getXpReward(),
                req.getKeywordsList().toArray(String[]::new), req.getIsPublished());
    }

    private UpdateContentCommand toUpdateCommand(UpdateContentRequest req) {
        return new UpdateContentCommand(
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
    }

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

    private ContentResponse toContentResponse(ContentResult c) {
        ContentResponse.Builder builder = ContentResponse.newBuilder()
                .setId(c.id().toString())
                .setTitle(c.title())
                .setCategory(c.category())
                .setLevel(toProtoLevel(c.level()))
                .setDurationMin(c.durationMin())
                .setXpReward(c.xpReward())
                .setIsPublished(c.published())
                .setReadCount(c.readCount())
                .setCreatedAt(toTimestamp(c.createdAt()))
                .setUpdatedAt(toTimestamp(c.updatedAt()));
        if (c.description() != null) builder.setDescription(c.description());
        if (c.keywords() != null) builder.addAllKeywords(List.of(c.keywords()));
        return builder.build();
    }

    private ContentDetailResponse.Builder toContentDetailBuilder(ContentResult c) {
        ContentDetailResponse.Builder builder = ContentDetailResponse.newBuilder()
                .setId(c.id().toString())
                .setTitle(c.title())
                .setCategory(c.category())
                .setLevel(toProtoLevel(c.level()))
                .setDurationMin(c.durationMin())
                .setXpReward(c.xpReward())
                .setIsPublished(c.published())
                .setReadCount(c.readCount())
                .setCreatedAt(toTimestamp(c.createdAt()))
                .setUpdatedAt(toTimestamp(c.updatedAt()));
        if (c.description() != null) builder.setDescription(c.description());
        if (c.body() != null) builder.setBody(c.body());
        if (c.keywords() != null) builder.addAllKeywords(List.of(c.keywords()));
        return builder;
    }

    private UserContentStateResponse toStateResponse(ContentStateResult s) {
        UserContentStateResponse.Builder builder = UserContentStateResponse.newBuilder()
                .setId(s.id().toString())
                .setContentId(s.contentId().toString())
                .setProgressPct(s.progressPct())
                .setIsCompleted(s.completed())
                .setIsFavorite(s.favorite());
        if (s.completedAt() != null) builder.setCompletedAt(toTimestamp(s.completedAt()));
        if (s.lastReadAt() != null) builder.setLastReadAt(toTimestamp(s.lastReadAt()));
        return builder.build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private ListContentsResponse toListResponse(Page<ContentResult> page, UUID userId) {
        List<ContentWithStateResponse> items = page.getContent().stream()
                .map(c -> {
                    ContentWithStateResponse.Builder b = ContentWithStateResponse.newBuilder()
                            .setContent(toContentResponse(c));
                    stateService.findState(userId, c.id())
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

    private String hashOf(String... parts) {
        return String.join("|", parts);
    }
}
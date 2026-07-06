package org.profit.candle.ranking.ranking.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingRequest;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingResponse;
import org.profit.candle.proto.ranking.v1.GetMyRankingRequest;
import org.profit.candle.proto.ranking.v1.GetMyRankingResponse;
import org.profit.candle.proto.ranking.v1.ListRankingsRequest;
import org.profit.candle.proto.ranking.v1.ListRankingsResponse;
import org.profit.candle.proto.ranking.v1.RankingEntry;
import org.profit.candle.proto.ranking.v1.RankingServiceGrpc;
import org.profit.candle.proto.common.v1.PageResponse;
import org.profit.candle.ranking.ranking.dto.DailyRankingResult;
import org.profit.candle.ranking.ranking.dto.RankingResult;
import org.profit.candle.ranking.ranking.exception.RankingErrorCode;
import org.profit.candle.ranking.ranking.exception.RankingException;
import org.profit.candle.ranking.ranking.service.DailyRankingService;
import org.profit.candle.ranking.ranking.service.RankingQueryService;
import org.profit.candle.ranking.support.idempotency.IdempotencyContext;
import org.profit.candle.ranking.support.idempotency.RankingIdempotencyExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RankingGrpcService extends RankingServiceGrpc.RankingServiceImplBase {

    private final DailyRankingService dailyRankingService;
    private final RankingQueryService rankingQueryService;
    private final RankingIdempotencyExecutor idempotencyExecutor;

    /** Batch의 일별 마감 요청을 멱등하게 실행하고 저장된 사용자 수를 반환한다. */
    @Override
    public void finalizeDailyRanking(
            FinalizeDailyRankingRequest request,
            StreamObserver<FinalizeDailyRankingResponse> responseObserver) {
        try {
            FinalizeDailyRankingResponse response = idempotencyExecutor.execute(
                    request, () -> toResponse(dailyRankingService.finalizeDailyRanking(parseDate(request))));
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException exception) {
            responseObserver.onError(exception);
        } catch (RankingException exception) {
            responseObserver.onError(toStatus(exception).asRuntimeException());
        } catch (Exception exception) {
            responseObserver.onError(Status.INTERNAL.withDescription("RANKING_INTERNAL_ERROR")
                    .asRuntimeException());
        }
    }

    /** 마지막 완료일 기준 TOP 랭킹을 cursor 방식으로 반환한다. */
    @Override
    public void listRankings(
            ListRankingsRequest request,
            StreamObserver<ListRankingsResponse> responseObserver) {
        try {
            var page = rankingQueryService.listRankings(
                    request.hasPage() ? request.getPage().getPageSize() : 0,
                    request.hasPage() ? request.getPage().getPageToken() : "");
            responseObserver.onNext(ListRankingsResponse.newBuilder()
                    .addAllRankings(page.rankings().stream().map(this::toEntry).toList())
                    .setPage(PageResponse.newBuilder().setNextPageToken(page.nextPageToken()))
                    .build());
            responseObserver.onCompleted();
        } catch (RankingException exception) {
            responseObserver.onError(toStatus(exception).asRuntimeException());
        } catch (Exception exception) {
            responseObserver.onError(Status.INTERNAL.withDescription("RANKING_INTERNAL_ERROR")
                    .asRuntimeException());
        }
    }

    /** 인증 사용자의 마지막 완료 순위를 반환한다. */
    @Override
    public void getMyRanking(
            GetMyRankingRequest request,
            StreamObserver<GetMyRankingResponse> responseObserver) {
        try {
            UUID userId = authenticatedUser(request.getUserId());
            responseObserver.onNext(GetMyRankingResponse.newBuilder()
                    .setRanking(toEntry(rankingQueryService.getMyRanking(userId)))
                    .build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException exception) {
            responseObserver.onError(exception);
        } catch (RankingException exception) {
            responseObserver.onError(toStatus(exception).asRuntimeException());
        } catch (Exception exception) {
            responseObserver.onError(Status.INTERNAL.withDescription("RANKING_INTERNAL_ERROR")
                    .asRuntimeException());
        }
    }

    /** YYYY-MM-DD 형식의 KST 거래일을 LocalDate로 변환한다. */
    private LocalDate parseDate(FinalizeDailyRankingRequest request) {
        try {
            return LocalDate.parse(request.getRankingDate());
        } catch (DateTimeParseException exception) {
            throw Status.INVALID_ARGUMENT.withDescription("RANKING_DATE_INVALID").asRuntimeException();
        }
    }

    /** 도메인 계산 결과를 protobuf 응답으로 변환한다. */
    private FinalizeDailyRankingResponse toResponse(DailyRankingResult result) {
        return FinalizeDailyRankingResponse.newBuilder()
                .setRankingDate(result.rankingDate().toString())
                .setRankedUserCount(result.rankedUserCount())
                .build();
    }

    /** 조회 DTO를 Ranking protobuf 항목으로 변환한다. */
    private RankingEntry toEntry(RankingResult result) {
        return RankingEntry.newBuilder()
                .setRank(result.position())
                .setUserId(result.userId().toString())
                .setNickname(result.nickname())
                .setReturnRate(result.profitRate().toPlainString())
                .setTotalAsset(result.totalAsset())
                .setTradeCount(result.tradeCount())
                .setRankingDate(result.rankingDate().toString())
                .build();
    }

    /** metadata 인증 주체와 request user_id가 일치하는지 검사한다. */
    private UUID authenticatedUser(String requestedUserId) {
        IdempotencyContext context = IdempotencyContext.current();
        if (context == null || context.actorId() == null || context.actorId().isBlank()) {
            throw Status.UNAUTHENTICATED.withDescription("MISSING_ACTOR").asRuntimeException();
        }
        if (!context.actorId().equals(requestedUserId)) {
            throw Status.PERMISSION_DENIED.withDescription("RANKING_ACCESS_DENIED").asRuntimeException();
        }
        try {
            return UUID.fromString(requestedUserId);
        } catch (IllegalArgumentException exception) {
            throw Status.INVALID_ARGUMENT.withDescription("RANKING_USER_ID_INVALID").asRuntimeException();
        }
    }

    /** Ranking 오류를 호출자가 처리할 수 있는 gRPC 상태로 변환한다. */
    private Status toStatus(RankingException exception) {
        if (exception.errorCode() == RankingErrorCode.PORTFOLIO_SNAPSHOT_SERVICE_UNAVAILABLE) {
            return Status.UNAVAILABLE.withDescription(exception.errorCode().code());
        }
        if (exception.errorCode() == RankingErrorCode.RANKING_NOT_FOUND) {
            return Status.NOT_FOUND.withDescription(exception.errorCode().code());
        }
        if (exception.errorCode() == RankingErrorCode.INVALID_PAGE_TOKEN) {
            return Status.INVALID_ARGUMENT.withDescription(exception.errorCode().code());
        }
        return Status.FAILED_PRECONDITION.withDescription(exception.errorCode().code());
    }
}

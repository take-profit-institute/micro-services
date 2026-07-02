package org.profit.candle.ranking.ranking.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingRequest;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingResponse;
import org.profit.candle.proto.ranking.v1.RankingServiceGrpc;
import org.profit.candle.ranking.ranking.dto.DailyRankingResult;
import org.profit.candle.ranking.ranking.exception.RankingErrorCode;
import org.profit.candle.ranking.ranking.exception.RankingException;
import org.profit.candle.ranking.ranking.service.DailyRankingService;
import org.profit.candle.ranking.support.idempotency.RankingIdempotencyExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RankingGrpcService extends RankingServiceGrpc.RankingServiceImplBase {

    private final DailyRankingService dailyRankingService;
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

    /** Ranking 오류를 호출자가 처리할 수 있는 gRPC 상태로 변환한다. */
    private Status toStatus(RankingException exception) {
        if (exception.errorCode() == RankingErrorCode.PORTFOLIO_SNAPSHOT_SERVICE_UNAVAILABLE) {
            return Status.UNAVAILABLE.withDescription(exception.errorCode().code());
        }
        return Status.FAILED_PRECONDITION.withDescription(exception.errorCode().code());
    }
}

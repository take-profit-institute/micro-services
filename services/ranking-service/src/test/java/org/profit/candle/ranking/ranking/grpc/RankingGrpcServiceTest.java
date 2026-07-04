package org.profit.candle.ranking.ranking.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.proto.common.v1.PageRequest;
import org.profit.candle.proto.ranking.v1.GetMyRankingRequest;
import org.profit.candle.proto.ranking.v1.GetMyRankingResponse;
import org.profit.candle.proto.ranking.v1.ListRankingsRequest;
import org.profit.candle.proto.ranking.v1.ListRankingsResponse;
import org.profit.candle.ranking.ranking.dto.RankingPage;
import org.profit.candle.ranking.ranking.dto.RankingResult;
import org.profit.candle.ranking.ranking.service.DailyRankingService;
import org.profit.candle.ranking.ranking.service.RankingQueryService;
import org.profit.candle.ranking.support.idempotency.IdempotencyContext;
import org.profit.candle.ranking.support.idempotency.RankingIdempotencyExecutor;

@ExtendWith(MockitoExtension.class)
class RankingGrpcServiceTest {

    private static final UUID USER_ID = UUID.fromString("60000000-0000-4000-8000-000000000001");

    @Mock
    DailyRankingService dailyRankingService;

    @Mock
    RankingQueryService rankingQueryService;

    @Mock
    RankingIdempotencyExecutor idempotencyExecutor;

    @Mock
    StreamObserver<ListRankingsResponse> listObserver;

    @Mock
    StreamObserver<GetMyRankingResponse> myObserver;

    /** 조회 DTO가 RankingEntry와 다음 cursor로 정확히 변환되는지 검증한다. */
    @Test
    void listRankingsMapsServiceResultToProto() {
        RankingResult ranking = ranking(USER_ID, 1);
        when(rankingQueryService.listRankings(20, "cursor"))
                .thenReturn(new RankingPage(List.of(ranking), "next"));
        RankingGrpcService service = service();

        service.listRankings(ListRankingsRequest.newBuilder()
                .setPage(PageRequest.newBuilder().setPageSize(20).setPageToken("cursor"))
                .build(), listObserver);

        ArgumentCaptor<ListRankingsResponse> captor = ArgumentCaptor.forClass(ListRankingsResponse.class);
        verify(listObserver).onNext(captor.capture());
        verify(listObserver).onCompleted();
        assertThat(captor.getValue().getRankings(0).getUserId()).isEqualTo(USER_ID.toString());
        assertThat(captor.getValue().getRankings(0).getReturnRate()).isEqualTo("12.5000");
        assertThat(captor.getValue().getPage().getNextPageToken()).isEqualTo("next");
    }

    /** metadata 인증 사용자와 request user_id가 같을 때 내 순위를 반환하는지 검증한다. */
    @Test
    void getMyRankingReturnsAuthenticatedUsersRanking() throws Exception {
        when(rankingQueryService.getMyRanking(USER_ID)).thenReturn(ranking(USER_ID, 3));
        RankingGrpcService service = service();
        IdempotencyContext context = new IdempotencyContext(
                USER_ID.toString(), "candle.ranking.v1.RankingService/GetMyRanking", null);

        Context.current().withValue(IdempotencyContext.CONTEXT_KEY, context).call(() -> {
            service.getMyRanking(GetMyRankingRequest.newBuilder()
                    .setUserId(USER_ID.toString()).build(), myObserver);
            return null;
        });

        ArgumentCaptor<GetMyRankingResponse> captor = ArgumentCaptor.forClass(GetMyRankingResponse.class);
        verify(myObserver).onNext(captor.capture());
        verify(myObserver).onCompleted();
        assertThat(captor.getValue().getRanking().getRank()).isEqualTo(3);
    }

    /** 인증 사용자와 다른 user_id 조회를 PERMISSION_DENIED로 거절하는지 검증한다. */
    @Test
    void getMyRankingRejectsAnotherUser() throws Exception {
        RankingGrpcService service = service();
        IdempotencyContext context = new IdempotencyContext(
                USER_ID.toString(), "candle.ranking.v1.RankingService/GetMyRanking", null);

        Context.current().withValue(IdempotencyContext.CONTEXT_KEY, context).call(() -> {
            service.getMyRanking(GetMyRankingRequest.newBuilder()
                    .setUserId("60000000-0000-4000-8000-000000000002").build(), myObserver);
            return null;
        });

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(myObserver).onError(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    /** 테스트 대상 gRPC 서비스를 생성한다. */
    private RankingGrpcService service() {
        return new RankingGrpcService(dailyRankingService, rankingQueryService, idempotencyExecutor);
    }

    /** 테스트용 랭킹 조회 결과를 만든다. */
    private RankingResult ranking(UUID userId, int position) {
        return new RankingResult(
                position, userId, "chanmi", 120_000L, new BigDecimal("12.5000"), 8,
                LocalDate.of(2026, 7, 3));
    }
}

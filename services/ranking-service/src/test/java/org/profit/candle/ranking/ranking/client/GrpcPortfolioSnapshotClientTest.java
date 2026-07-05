package org.profit.candle.ranking.ranking.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.proto.common.v1.PageResponse;
import org.profit.candle.proto.portfolio.v1.DailyPortfolioSnapshot;
import org.profit.candle.proto.portfolio.v1.ListDailyPortfolioSnapshotsRequest;
import org.profit.candle.proto.portfolio.v1.ListDailyPortfolioSnapshotsResponse;
import org.profit.candle.proto.portfolio.v1.PortfolioServiceGrpc;
import org.profit.candle.ranking.config.RankingGrpcProperties;
import org.profit.candle.ranking.ranking.exception.RankingErrorCode;
import org.profit.candle.ranking.ranking.exception.RankingException;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcPortfolioSnapshotClientTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** #105 요청과 응답을 Ranking client 모델로 정확히 변환하는지 검증한다. */
    @Test
    void listDailySnapshotsMapsRequestAndResponse() throws Exception {
        AtomicReference<ListDailyPortfolioSnapshotsRequest> captured = new AtomicReference<>();
        GrpcPortfolioSnapshotClient client = client(service((request, observer) -> {
            captured.set(request);
            observer.onNext(ListDailyPortfolioSnapshotsResponse.newBuilder()
                    .addSnapshots(DailyPortfolioSnapshot.newBuilder()
                            .setUserId("70000000-0000-4000-8000-000000000001")
                            .setTotalAsset(120_000L)
                            .setCumulativeReturnRate("12.3400"))
                    .setPage(PageResponse.newBuilder().setNextPageToken("next-token"))
                    .build());
            observer.onCompleted();
        }));

        PortfolioSnapshotPage page = client.listDailySnapshots(
                LocalDate.of(2026, 7, 5), "cursor", 500);

        assertThat(captured.get().getSnapshotDate()).isEqualTo("2026-07-05");
        assertThat(captured.get().getPage().getPageSize()).isEqualTo(500);
        assertThat(captured.get().getPage().getPageToken()).isEqualTo("cursor");
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.totalAsset()).isEqualTo(120_000L);
            assertThat(item.cumulativeReturnRate()).isEqualByComparingTo("12.3400");
        });
        assertThat(page.nextPageToken()).isEqualTo("next-token");
    }

    /** Portfolio gRPC 장애를 Ranking의 재시도 가능한 외부 서비스 오류로 변환하는지 검증한다. */
    @Test
    void listDailySnapshotsMapsGrpcFailure() throws Exception {
        GrpcPortfolioSnapshotClient client = client(service((request, observer) ->
                observer.onError(Status.UNAVAILABLE.asRuntimeException())));

        assertThatThrownBy(() -> client.listDailySnapshots(LocalDate.of(2026, 7, 5), "", 500))
                .isInstanceOf(RankingException.class)
                .satisfies(exception -> assertThat(((RankingException) exception).errorCode())
                        .isEqualTo(RankingErrorCode.PORTFOLIO_SNAPSHOT_SERVICE_UNAVAILABLE));
    }

    /** Portfolio가 잘못된 사용자 ID를 반환하면 저장 전에 거절하는지 검증한다. */
    @Test
    void listDailySnapshotsRejectsInvalidSnapshot() throws Exception {
        GrpcPortfolioSnapshotClient client = client(service((request, observer) -> {
            observer.onNext(ListDailyPortfolioSnapshotsResponse.newBuilder()
                    .addSnapshots(DailyPortfolioSnapshot.newBuilder()
                            .setUserId("invalid-user")
                            .setTotalAsset(120_000L)
                            .setCumulativeReturnRate("12.3400"))
                    .build());
            observer.onCompleted();
        }));

        assertThatThrownBy(() -> client.listDailySnapshots(LocalDate.of(2026, 7, 5), "", 500))
                .isInstanceOf(RankingException.class)
                .satisfies(exception -> assertThat(((RankingException) exception).errorCode())
                        .isEqualTo(RankingErrorCode.INVALID_PORTFOLIO_SNAPSHOT));
    }

    /** in-process Portfolio 서버에 연결된 실제 blocking stub client를 만든다. */
    private GrpcPortfolioSnapshotClient client(PortfolioServiceGrpc.PortfolioServiceImplBase service)
            throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        return new GrpcPortfolioSnapshotClient(
                channelFactory(channel),
                new RankingGrpcProperties(Duration.ofSeconds(1)));
    }

    /** 테스트별 응답 동작을 가진 Portfolio gRPC 서비스를 만든다. */
    private PortfolioServiceGrpc.PortfolioServiceImplBase service(
            GrpcCall call) {
        return new PortfolioServiceGrpc.PortfolioServiceImplBase() {
            @Override
            public void listDailyPortfolioSnapshots(
                    ListDailyPortfolioSnapshotsRequest request,
                    StreamObserver<ListDailyPortfolioSnapshotsResponse> responseObserver) {
                call.execute(request, responseObserver);
            }
        };
    }

    /** Spring gRPC channel factory를 in-process channel로 대체한다. */
    private GrpcChannelFactory channelFactory(ManagedChannel managedChannel) {
        return new GrpcChannelFactory() {
            @Override
            public boolean supports(String target) {
                return true;
            }

            @Override
            public ManagedChannel createChannel(String target, ChannelBuilderOptions options) {
                return managedChannel;
            }
        };
    }

    @FunctionalInterface
    private interface GrpcCall {
        void execute(
                ListDailyPortfolioSnapshotsRequest request,
                StreamObserver<ListDailyPortfolioSnapshotsResponse> responseObserver);
    }
}

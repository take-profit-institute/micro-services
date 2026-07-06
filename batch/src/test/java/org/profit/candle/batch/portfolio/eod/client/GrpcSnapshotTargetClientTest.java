package org.profit.candle.batch.portfolio.eod.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.profit.candle.proto.common.v1.PageResponse;
import org.profit.candle.proto.portfolio.v1.ActiveHolder;
import org.profit.candle.proto.portfolio.v1.HoldingServiceGrpc;
import org.profit.candle.proto.portfolio.v1.ListActiveHoldersRequest;
import org.profit.candle.proto.portfolio.v1.ListActiveHoldersResponse;
import org.profit.candle.proto.portfolio.v1.Position;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcSnapshotTargetClientTest {

    private static final Metadata.Key<String> ROLE = metadataKey("x-role");
    private static final Metadata.Key<String> REQUEST_ID = metadataKey("x-request-id");

    /** cursor 요청과 Portfolio 보유 포지션 응답 매핑을 검증한다. */
    @Test
    void loadsActiveHoldersWithCursorPage() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        AtomicReference<Metadata> metadata = new AtomicReference<>();
        AtomicReference<ListActiveHoldersRequest> request = new AtomicReference<>();
        Server server = startServer(serverName, metadata, request);
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        try {
            GrpcSnapshotTargetClient client = new GrpcSnapshotTargetClient(
                    channelFactory(channel),
                    properties(),
                    new RequestIdGenerator() {
                        @Override
                        public String generate() {
                            return "request-portfolio-targets";
                        }
                    }
            );

            SnapshotTarget.Page page = client.loadTargets(
                    LocalDate.of(2026, 7, 6),
                    "user-0",
                    100
            );

            assertThat(request.get().getPage().getPageSize()).isEqualTo(100);
            assertThat(request.get().getPage().getPageToken()).isEqualTo("user-0");
            assertThat(metadata.get().get(ROLE)).isEqualTo("SYSTEM");
            assertThat(metadata.get().get(REQUEST_ID)).isEqualTo("request-portfolio-targets");
            assertThat(page.nextPageToken()).isEqualTo("user-1");
            assertThat(page.targets()).containsExactly(new SnapshotTarget(
                    "user-1",
                    java.util.List.of(new SnapshotTarget.Holding("005930", 3, 70_000))
            ));
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** in-process Portfolio HoldingService를 실행한다. */
    private Server startServer(
            String serverName,
            AtomicReference<Metadata> metadata,
            AtomicReference<ListActiveHoldersRequest> request
    ) throws Exception {
        HoldingServiceGrpc.HoldingServiceImplBase service =
                new HoldingServiceGrpc.HoldingServiceImplBase() {
                    @Override
                    public void listActiveHolders(
                            ListActiveHoldersRequest value,
                            StreamObserver<ListActiveHoldersResponse> observer
                    ) {
                        request.set(value);
                        observer.onNext(ListActiveHoldersResponse.newBuilder()
                                .addHolders(ActiveHolder.newBuilder()
                                        .setUserId("user-1")
                                        .addPositions(Position.newBuilder()
                                                .setSymbol("005930")
                                                .setQuantity(3)
                                                .setAveragePrice(70_000)
                                                .build())
                                        .build())
                                .setPage(PageResponse.newBuilder()
                                        .setNextPageToken("user-1")
                                        .build())
                                .build());
                        observer.onCompleted();
                    }
                };
        return InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(service, capture(metadata)))
                .build()
                .start();
    }

    /** 요청 metadata를 캡처한다. */
    private ServerInterceptor capture(AtomicReference<Metadata> metadata) {
        return new ServerInterceptor() {
            @Override
            public <RequestT, ResponseT> ServerCall.Listener<RequestT> interceptCall(
                    ServerCall<RequestT, ResponseT> call,
                    Metadata headers,
                    ServerCallHandler<RequestT, ResponseT> next
            ) {
                metadata.set(headers);
                return next.startCall(call, headers);
            }
        };
    }

    /** 테스트 채널을 Spring gRPC factory로 제공한다. */
    private GrpcChannelFactory channelFactory(ManagedChannel channel) {
        return new GrpcChannelFactory() {
            @Override
            public boolean supports(String target) {
                return true;
            }

            @Override
            public ManagedChannel createChannel(String target, ChannelBuilderOptions options) {
                return channel;
            }
        };
    }

    /** 실제 운영과 동일한 gRPC channel 이름과 timeout 설정을 생성한다. */
    private BatchProperties properties() {
        return new BatchProperties(
                new BatchProperties.Schedule(
                        "Asia/Seoul",
                        new BatchProperties.Smoke(false, ""),
                        new BatchProperties.PortfolioEod(false, "", 100, 500),
                        new BatchProperties.StockSync(false, "")
                ),
                new BatchProperties.Grpc(
                        "market",
                        "stock",
                        "trading",
                        "portfolio",
                        300,
                        1_000,
                        120_000
                )
        );
    }

    private static Metadata.Key<String> metadataKey(String name) {
        return Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
    }
}

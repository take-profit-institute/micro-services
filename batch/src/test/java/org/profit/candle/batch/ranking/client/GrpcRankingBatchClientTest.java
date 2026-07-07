package org.profit.candle.batch.ranking.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.ranking.config.RankingBatchProperties;
import org.profit.candle.batch.ranking.exception.RankingBatchException;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingRequest;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingResponse;
import org.profit.candle.proto.ranking.v1.RankingServiceGrpc;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcRankingBatchClientTest {

    private static final Metadata.Key<String> USER_ID = key("x-user-id");
    private static final Metadata.Key<String> ROLE = key("x-role");
    private static final Metadata.Key<String> IDEMPOTENCY_KEY = key("x-idempotency-key");

    /** metadata와 request에 같은 key를 전달하고 응답을 매핑하는지 검증한다. */
    @Test
    void finalizesDailyRankingWithSameIdempotencyKey() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        AtomicReference<Metadata> metadata = new AtomicReference<>();
        AtomicReference<FinalizeDailyRankingRequest> request = new AtomicReference<>();
        Server server = startServer(serverName, metadata, request);
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        try {
            GrpcRankingBatchClient client = new GrpcRankingBatchClient(
                    channelFactory(channel),
                    new RankingBatchProperties("ranking", 120_000)
            );
            LocalDate rankingDate = LocalDate.of(2026, 7, 6);
            String idempotencyKey = "8de47f7d-689f-3b62-a16f-12173fb65d4f";

            RankingBatchClient.Result result = client.finalizeDailyRanking(
                    rankingDate,
                    idempotencyKey
            );

            assertThat(metadata.get().get(USER_ID)).isEqualTo("batch-service");
            assertThat(metadata.get().get(ROLE)).isEqualTo("SYSTEM");
            assertThat(metadata.get().get(IDEMPOTENCY_KEY)).isEqualTo(idempotencyKey);
            assertThat(request.get().getRankingDate()).isEqualTo("2026-07-06");
            assertThat(request.get().getCommandMetadata().getIdempotencyKey())
                    .isEqualTo(idempotencyKey);
            assertThat(result).isEqualTo(new RankingBatchClient.Result(rankingDate, 42));
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 일시적인 gRPC 장애를 재시도 가능한 Batch 오류로 변환하는지 검증한다. */
    @Test
    void mapsUnavailableToRetryableError() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        RankingServiceGrpc.RankingServiceImplBase service =
                new RankingServiceGrpc.RankingServiceImplBase() {
                    @Override
                    public void finalizeDailyRanking(
                            FinalizeDailyRankingRequest request,
                            StreamObserver<FinalizeDailyRankingResponse> observer
                    ) {
                        observer.onError(Status.UNAVAILABLE.asRuntimeException());
                    }
                };
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        try {
            GrpcRankingBatchClient client = new GrpcRankingBatchClient(
                    channelFactory(channel),
                    new RankingBatchProperties("ranking", 120_000)
            );

            assertThatThrownBy(() -> client.finalizeDailyRanking(
                    LocalDate.of(2026, 7, 6),
                    "8de47f7d-689f-3b62-a16f-12173fb65d4f"
            ))
                    .isInstanceOf(RankingBatchException.class)
                    .satisfies(exception -> assertThat(
                            ((RankingBatchException) exception).retryable()
                    ).isTrue());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** in-process RankingService를 실행하고 요청 정보를 수집한다. */
    private Server startServer(
            String serverName,
            AtomicReference<Metadata> metadata,
            AtomicReference<FinalizeDailyRankingRequest> request
    ) throws Exception {
        RankingServiceGrpc.RankingServiceImplBase service =
                new RankingServiceGrpc.RankingServiceImplBase() {
                    @Override
                    public void finalizeDailyRanking(
                            FinalizeDailyRankingRequest value,
                            StreamObserver<FinalizeDailyRankingResponse> observer
                    ) {
                        request.set(value);
                        observer.onNext(FinalizeDailyRankingResponse.newBuilder()
                                .setRankingDate(value.getRankingDate())
                                .setRankedUserCount(42)
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

    /** gRPC metadata를 캡처한다. */
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

    /** in-process channel을 Spring gRPC client에 제공한다. */
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

    private static Metadata.Key<String> key(String name) {
        return Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
    }
}

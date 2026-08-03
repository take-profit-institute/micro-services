package org.profit.candle.batch.stock.candle.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.stock.candle.exception.StockCandleErrorCode;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;
import org.profit.candle.proto.stock.v1.BackfillCandlesRequest;
import org.profit.candle.proto.stock.v1.BackfillCandlesResponse;
import org.profit.candle.proto.stock.v1.ChartServiceGrpc;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcCandleBackfillClientTest {

    @Test
    void backfillDaily_returnsUpsertedCount() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ChartServiceGrpc.ChartServiceImplBase service = new ChartServiceGrpc.ChartServiceImplBase() {
            @Override
            public void backfillCandles(
                    BackfillCandlesRequest request,
                    StreamObserver<BackfillCandlesResponse> observer
            ) {
                calls.incrementAndGet();
                observer.onNext(BackfillCandlesResponse.newBuilder().setUpserted(9).build());
                observer.onCompleted();
            }
        };

        try (TestServer testServer = startServer(service)) {
            CandleBackfillClient client = client(testServer);

            assertThat(client.backfillDaily("000001", 10)).isEqualTo(9);
            assertThat(calls.get()).isEqualTo(1);
        }
    }

    /**
     * 키움 429는 stock-service가 자체 백오프를 소진한 뒤 RESOURCE_EXHAUSTED 로 돌려준다.
     * 배치가 이걸 재시도 가능으로 보면 종목당 호출이 서버 재시도와 곱해진다.
     */
    @Test
    void backfillDaily_mapsResourceExhaustedToNonRetryableRateLimit() throws Exception {
        try (TestServer testServer = startServer(failing(Status.RESOURCE_EXHAUSTED))) {
            CandleBackfillClient client = client(testServer);

            assertThatThrownBy(() -> client.backfillDaily("000001", 10))
                    .isInstanceOfSatisfying(StockCandleException.class, exception -> {
                        assertThat(exception.retryable()).isFalse();
                        assertThat(exception.errorCode())
                                .isEqualTo(StockCandleErrorCode.EXTERNAL_RATE_LIMITED);
                    });
        }
    }

    @Test
    void backfillDaily_keepsTransportFailuresRetryable() throws Exception {
        try (TestServer testServer = startServer(failing(Status.UNAVAILABLE))) {
            CandleBackfillClient client = client(testServer);

            assertThatThrownBy(() -> client.backfillDaily("000001", 10))
                    .isInstanceOfSatisfying(StockCandleException.class, exception ->
                            assertThat(exception.retryable()).isTrue());
        }
    }

    private static ChartServiceGrpc.ChartServiceImplBase failing(Status status) {
        return new ChartServiceGrpc.ChartServiceImplBase() {
            @Override
            public void backfillCandles(
                    BackfillCandlesRequest request,
                    StreamObserver<BackfillCandlesResponse> observer
            ) {
                observer.onError(status.asRuntimeException());
            }
        };
    }

    private CandleBackfillClient client(TestServer testServer) {
        return new GrpcCandleBackfillClient(channelFactory(testServer.channel()), properties());
    }

    private TestServer startServer(ChartServiceGrpc.ChartServiceImplBase service) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        return new TestServer(server, channel);
    }

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

    private BatchProperties properties() {
        return new BatchProperties(
                new BatchProperties.Schedule(
                        "Asia/Seoul",
                        new BatchProperties.Smoke(false, "0 0 * * * *"),
                        new BatchProperties.PortfolioEod(false, "0 0 16 * * MON-FRI", 100, 500),
                        new BatchProperties.StockSync(false, "0 30 16 * * MON-FRI"),
                        new BatchProperties.Trading(false, "", "", "", "")
                ),
                new BatchProperties.Grpc("market", "stock", "trading", "portfolio", 300, 1_000, 120_000, 120_000)
        );
    }

    private record TestServer(Server server, ManagedChannel channel) implements AutoCloseable {

        @Override
        public void close() throws InterruptedException {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}

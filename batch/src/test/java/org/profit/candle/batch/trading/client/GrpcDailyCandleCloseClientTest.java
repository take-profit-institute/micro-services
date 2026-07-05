package org.profit.candle.batch.trading.client;

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
import org.profit.candle.proto.stock.v1.ChartServiceGrpc;
import org.profit.candle.proto.stock.v1.CloseDailyCandlesRequest;
import org.profit.candle.proto.stock.v1.CloseDailyCandlesResponse;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcDailyCandleCloseClientTest {

    private static final Metadata.Key<String> IDEMPOTENCY_KEY = Metadata.Key.of(
            "x-idempotency-key",
            Metadata.ASCII_STRING_MARSHALLER
    );

    @Test
    void sendsTradeDateAndSameIdempotencyKey() throws Exception {
        AtomicReference<CloseDailyCandlesRequest> request = new AtomicReference<>();
        AtomicReference<Metadata> metadata = new AtomicReference<>();
        ChartServiceGrpc.ChartServiceImplBase service =
                new ChartServiceGrpc.ChartServiceImplBase() {
                    @Override
                    public void closeDailyCandles(
                            CloseDailyCandlesRequest value,
                            StreamObserver<CloseDailyCandlesResponse> observer
                    ) {
                        request.set(value);
                        observer.onNext(CloseDailyCandlesResponse.newBuilder()
                                .setClosedCount(25)
                                .build());
                        observer.onCompleted();
                    }
                };

        try (TestServer server = startServer(service, metadata)) {
            DailyCandleCloseClient client = new GrpcDailyCandleCloseClient(
                    channelFactory(server.channel()),
                    properties()
            );

            int closed = client.close(
                    LocalDate.of(2026, 7, 6),
                    "stock-close:2026-07-06"
            );

            assertThat(closed).isEqualTo(25);
            assertThat(request.get().getTradeDate()).isEqualTo("2026-07-06");
            assertThat(request.get().getIdempotencyKey())
                    .isEqualTo("stock-close:2026-07-06");
            assertThat(metadata.get().get(IDEMPOTENCY_KEY))
                    .isEqualTo(request.get().getIdempotencyKey());
        }
    }

    private TestServer startServer(
            ChartServiceGrpc.ChartServiceImplBase service,
            AtomicReference<Metadata> capturedMetadata
    ) throws Exception {
        String name = InProcessServerBuilder.generateName();
        ServerInterceptor interceptor = new ServerInterceptor() {
            @Override
            public <RequestT, ResponseT> ServerCall.Listener<RequestT> interceptCall(
                    ServerCall<RequestT, ResponseT> call,
                    Metadata headers,
                    ServerCallHandler<RequestT, ResponseT> next
            ) {
                capturedMetadata.set(headers);
                return next.startCall(call, headers);
            }
        };
        Server server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(ServerInterceptors.intercept(service, interceptor))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(name)
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
            public ManagedChannel createChannel(
                    String target,
                    ChannelBuilderOptions options
            ) {
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
                        new BatchProperties.StockSync(false, "0 30 16 * * MON-FRI")
                ),
                new BatchProperties.Grpc(
                        "market",
                        "stock",
                        "trading",
                        "portfolio",
                        300,
                        1_000,
                        120_000,
                        120_000
                )
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

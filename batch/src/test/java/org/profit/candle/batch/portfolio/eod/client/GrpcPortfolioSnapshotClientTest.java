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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;
import org.profit.candle.proto.portfolio.v1.PortfolioServiceGrpc;
import org.profit.candle.proto.portfolio.v1.RecordDailySnapshotRequest;
import org.profit.candle.proto.portfolio.v1.RecordDailySnapshotResponse;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcPortfolioSnapshotClientTest {

    private static final Metadata.Key<String> USER_ID = metadataKey("x-user-id");
    private static final Metadata.Key<String> ROLE = metadataKey("x-role");
    private static final Metadata.Key<String> REQUEST_ID = metadataKey("x-request-id");
    private static final Metadata.Key<String> IDEMPOTENCY_KEY =
            metadataKey("x-idempotency-key");

    @Test
    void shouldSendSameIdempotencyKeyInMetadataAndRequest() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        AtomicReference<Metadata> capturedMetadata = new AtomicReference<>();
        AtomicReference<RecordDailySnapshotRequest> capturedRequest = new AtomicReference<>();
        Server server = startServer(serverName, capturedMetadata, capturedRequest);
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        try {
            GrpcPortfolioSnapshotClient client = new GrpcPortfolioSnapshotClient(
                    channelFactory(channel),
                    properties(),
                    new RequestIdGenerator() {
                        @Override
                        public String generate() {
                            return "73cb891b-9d75-4e92-ae18-163731fc224f";
                        }
                    }
            );
            SnapshotCommand command = new SnapshotCommand(
                    "user-1",
                    LocalDate.of(2026, 6, 29),
                    150_000,
                    100_000,
                    120_000,
                    "50f1e44e-8b98-4de7-a949-28ee63038a4d"
            );

            client.recordDailySnapshot(command);

            assertThat(capturedMetadata.get().get(USER_ID)).isEqualTo("user-1");
            assertThat(capturedMetadata.get().get(ROLE)).isEqualTo("SYSTEM");
            assertThat(capturedMetadata.get().get(REQUEST_ID))
                    .isEqualTo("73cb891b-9d75-4e92-ae18-163731fc224f");
            assertThat(capturedMetadata.get().get(IDEMPOTENCY_KEY))
                    .isEqualTo(command.idempotencyKey());
            assertThat(capturedRequest.get().getIdempotencyKey())
                    .isEqualTo(command.idempotencyKey());
            assertThat(capturedRequest.get().getUserId()).isEqualTo(command.userId());
            assertThat(capturedRequest.get().getSnapshotDate())
                    .isEqualTo(command.businessDate().toString());
            assertThat(capturedRequest.get().getTotalAsset()).isEqualTo(command.totalAsset());
            assertThat(capturedRequest.get().getStockValue()).isEqualTo(command.stockValue());
            assertThat(capturedRequest.get().getSeedCapital()).isEqualTo(command.seedCapital());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Server startServer(
            String serverName,
            AtomicReference<Metadata> capturedMetadata,
            AtomicReference<RecordDailySnapshotRequest> capturedRequest
    ) throws Exception {
        PortfolioServiceGrpc.PortfolioServiceImplBase service =
                new PortfolioServiceGrpc.PortfolioServiceImplBase() {
                    @Override
                    public void recordDailySnapshot(
                            RecordDailySnapshotRequest request,
                            StreamObserver<RecordDailySnapshotResponse> observer
                    ) {
                        capturedRequest.set(request);
                        observer.onNext(RecordDailySnapshotResponse.getDefaultInstance());
                        observer.onCompleted();
                    }
                };
        return InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(
                        service,
                        new ServerInterceptor() {
                            @Override
                            public <RequestT, ResponseT>
                                    ServerCall.Listener<RequestT> interceptCall(
                                            ServerCall<RequestT, ResponseT> call,
                                            Metadata headers,
                                            ServerCallHandler<RequestT, ResponseT> next
                                    ) {
                                capturedMetadata.set(headers);
                                return next.startCall(call, headers);
                            }
                        }
                ))
                .build()
                .start();
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
                        new BatchProperties.PortfolioEod(
                                false,
                                "0 0 16 * * MON-FRI",
                                100,
                                500
                        ),
                        new BatchProperties.StockSync(false, "0 30 16 * * MON-FRI"),
                        new BatchProperties.Trading(false, "", "", "", "")
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

    private static Metadata.Key<String> metadataKey(String name) {
        return Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
    }
}

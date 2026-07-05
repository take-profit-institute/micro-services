package org.profit.candle.batch.portfolio.eod.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.profit.candle.proto.trading.v1.AccountBalance;
import org.profit.candle.proto.trading.v1.AccountServiceGrpc;
import org.profit.candle.proto.trading.v1.GetBalanceRequest;
import org.profit.candle.proto.trading.v1.GetBalanceResponse;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcCashBalanceClientTest {

    private static final Metadata.Key<String> USER_ID = metadataKey("x-user-id");
    private static final Metadata.Key<String> ROLE = metadataKey("x-role");
    private static final Metadata.Key<String> REQUEST_ID = metadataKey("x-request-id");

    @Test
    void loadsCashFromAvailableAndReservedBalances() throws Exception {
        AtomicReference<Metadata> capturedMetadata = new AtomicReference<>();
        AtomicReference<GetBalanceRequest> capturedRequest = new AtomicReference<>();
        AccountServiceGrpc.AccountServiceImplBase service = balanceService(
                GetBalanceResponse.newBuilder()
                        .setBalance(AccountBalance.newBuilder()
                                .setUserId("user-1")
                                .setCash(150_000)
                                .setAvailableCash(120_000)
                                .setReservedBalance(30_000)
                                .build())
                        .build(),
                capturedRequest
        );

        try (TestServer testServer = startServer(service, capturedMetadata)) {
            GrpcCashBalanceClient client = client(testServer.channel());

            long cash = client.getCash("user-1");

            assertThat(cash).isEqualTo(150_000);
            assertThat(capturedRequest.get().getUserId()).isEqualTo("user-1");
            assertThat(capturedMetadata.get().get(USER_ID)).isEqualTo("user-1");
            assertThat(capturedMetadata.get().get(ROLE)).isEqualTo("SYSTEM");
            assertThat(capturedMetadata.get().get(REQUEST_ID)).isEqualTo("request-1");
        }
    }

    @Test
    void rejectsResponseWithoutBalance() throws Exception {
        AccountServiceGrpc.AccountServiceImplBase service = balanceService(
                GetBalanceResponse.getDefaultInstance(),
                new AtomicReference<>()
        );

        try (TestServer testServer = startServer(service, new AtomicReference<>())) {
            GrpcCashBalanceClient client = client(testServer.channel());

            assertThatThrownBy(() -> client.getCash("user-1"))
                    .isInstanceOf(EodBatchException.class)
                    .satisfies(throwable -> assertThat(
                            ((EodBatchException) throwable).errorCode()
                    ).isEqualTo(EodBatchErrorCode.TRADING_BALANCE_MISSING));
        }
    }

    private AccountServiceGrpc.AccountServiceImplBase balanceService(
            GetBalanceResponse response,
            AtomicReference<GetBalanceRequest> capturedRequest
    ) {
        return new AccountServiceGrpc.AccountServiceImplBase() {
            @Override
            public void getBalance(
                    GetBalanceRequest request,
                    StreamObserver<GetBalanceResponse> observer
            ) {
                capturedRequest.set(request);
                observer.onNext(response);
                observer.onCompleted();
            }
        };
    }

    private TestServer startServer(
            AccountServiceGrpc.AccountServiceImplBase service,
            AtomicReference<Metadata> capturedMetadata
    ) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(ServerInterceptors.intercept(service, metadataInterceptor(
                        capturedMetadata
                )))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        return new TestServer(server, channel);
    }

    private ServerInterceptor metadataInterceptor(AtomicReference<Metadata> capturedMetadata) {
        return new ServerInterceptor() {
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
    }

    private GrpcCashBalanceClient client(ManagedChannel channel) {
        return new GrpcCashBalanceClient(
                channelFactory(channel),
                properties(),
                new RequestIdGenerator() {
                    @Override
                    public String generate() {
                        return "request-1";
                    }
                }
        );
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
                        new BatchProperties.StockSync(false, "0 30 16 * * MON-FRI")
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

    private record TestServer(Server server, ManagedChannel channel) implements AutoCloseable {

        @Override
        public void close() throws InterruptedException {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}

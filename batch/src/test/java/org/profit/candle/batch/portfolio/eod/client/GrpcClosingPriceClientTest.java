package org.profit.candle.batch.portfolio.eod.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.profit.candle.proto.stock.v1.ChartServiceGrpc;
import org.profit.candle.proto.stock.v1.GetPreviousCloseRequest;
import org.profit.candle.proto.stock.v1.GetPreviousCloseResponse;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcClosingPriceClientTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 5);

    @Test
    void loadsBusinessDateClosingPricesFromStockService() throws Exception {
        List<GetPreviousCloseRequest> requests = new ArrayList<>();
        ChartServiceGrpc.ChartServiceImplBase service = new ChartServiceGrpc.ChartServiceImplBase() {
            @Override
            public void getPreviousClose(
                    GetPreviousCloseRequest request,
                    StreamObserver<GetPreviousCloseResponse> observer
            ) {
                requests.add(request);
                long price = request.getCode().equals("005930") ? 70_000 : 110_000;
                observer.onNext(GetPreviousCloseResponse.newBuilder()
                        .setCode(request.getCode())
                        .setPrevClose(price)
                        .setPrevOpenTime(timestamp(
                                BUSINESS_DATE.atStartOfDay(KST).toInstant()
                        ))
                        .build());
                observer.onCompleted();
            }
        };

        try (TestServer testServer = startServer(service)) {
            GrpcClosingPriceClient client = new GrpcClosingPriceClient(
                    channelFactory(testServer.channel()),
                    properties(),
                    new RequestIdGenerator()
            );

            List<ClosingPrice> prices = client.loadClosingPrices(
                    BUSINESS_DATE,
                    List.of("005930", "000660")
            );

            assertThat(prices).extracting(ClosingPrice::symbol, ClosingPrice::price)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("005930", 70_000L),
                            org.assertj.core.groups.Tuple.tuple("000660", 110_000L)
                    );
            assertThat(requests).extracting(GetPreviousCloseRequest::getCode)
                    .containsExactly("005930", "000660");
            Instant expectedBaseDate = BUSINESS_DATE.plusDays(1)
                    .atStartOfDay(KST)
                    .toInstant();
            assertThat(requests)
                    .allSatisfy(request -> assertThat(toInstant(request.getDate()))
                            .isEqualTo(expectedBaseDate));
        }
    }

    private TestServer startServer(ChartServiceGrpc.ChartServiceImplBase service)
            throws Exception {
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
                        new BatchProperties.Trading(
                                false,
                                "0 0 8 * * MON-FRI",
                                "0 0 9 * * MON-FRI",
                                "0 31 15 * * MON-FRI",
                                "0 40 15 * * MON-FRI"
                        )
                ),
                new BatchProperties.Grpc(
                        "market",
                        "stock",
                        "trading",
                        "portfolio",
                        300,
                        1_000,
                        120_000,
                        30_000
                )
        );
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    private record TestServer(Server server, ManagedChannel channel) implements AutoCloseable {

        @Override
        public void close() throws InterruptedException {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}

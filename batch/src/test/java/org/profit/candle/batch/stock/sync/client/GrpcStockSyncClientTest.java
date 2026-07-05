package org.profit.candle.batch.stock.sync.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.proto.stock.v1.MarketType;
import org.profit.candle.proto.stock.v1.StockServiceGrpc;
import org.profit.candle.proto.stock.v1.SyncStocksRequest;
import org.profit.candle.proto.stock.v1.SyncStocksResponse;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcStockSyncClientTest {

    @Test
    void syncsRequestedMarketsAndMapsCounts() throws Exception {
        List<MarketType> requests = new ArrayList<>();
        StockServiceGrpc.StockServiceImplBase service =
                new StockServiceGrpc.StockServiceImplBase() {
                    @Override
                    public void syncStocks(
                            SyncStocksRequest request,
                            StreamObserver<SyncStocksResponse> observer
                    ) {
                        requests.add(request.getMarket());
                        observer.onNext(SyncStocksResponse.newBuilder()
                                .setUpserted(1_900)
                                .setTotal(2_000)
                                .build());
                        observer.onCompleted();
                    }
                };

        try (TestServer testServer = startServer(service)) {
            StockSyncClient client = new GrpcStockSyncClient(
                    channelFactory(testServer.channel()),
                    properties()
            );

            StockSyncClient.Result kospi = client.sync(StockSyncClient.Market.KOSPI);
            StockSyncClient.Result kosdaq = client.sync(StockSyncClient.Market.KOSDAQ);

            assertThat(requests).containsExactly(MarketType.KOSPI, MarketType.KOSDAQ);
            assertThat(kospi).isEqualTo(new StockSyncClient.Result(1_900, 2_000));
            assertThat(kosdaq).isEqualTo(new StockSyncClient.Result(1_900, 2_000));
        }
    }

    private TestServer startServer(StockServiceGrpc.StockServiceImplBase service)
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

    private record TestServer(Server server, ManagedChannel channel) implements AutoCloseable {

        @Override
        public void close() throws InterruptedException {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}

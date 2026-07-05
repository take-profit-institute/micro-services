package org.profit.candle.batch.trading.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.trading.exception.TradingBatchException;
import org.profit.candle.proto.trading.v1.ExpirePendingOrdersRequest;
import org.profit.candle.proto.trading.v1.ExpirePendingOrdersResponse;
import org.profit.candle.proto.trading.v1.ExpireReservationRequest;
import org.profit.candle.proto.trading.v1.ExpireReservationResponse;
import org.profit.candle.proto.trading.v1.FailStaleConvertingReservationRequest;
import org.profit.candle.proto.trading.v1.FailStaleConvertingReservationResponse;
import org.profit.candle.proto.trading.v1.ListExpirableReservationsRequest;
import org.profit.candle.proto.trading.v1.ListExpirableReservationsResponse;
import org.profit.candle.proto.trading.v1.ListStaleConvertingReservationsRequest;
import org.profit.candle.proto.trading.v1.ListStaleConvertingReservationsResponse;
import org.profit.candle.proto.trading.v1.OrderServiceGrpc;
import org.profit.candle.proto.trading.v1.ProcessOpenLimitReservationsRequest;
import org.profit.candle.proto.trading.v1.ProcessOpenLimitReservationsResponse;
import org.profit.candle.proto.trading.v1.ProcessPrevCloseReservationsRequest;
import org.profit.candle.proto.trading.v1.ProcessPrevCloseReservationsResponse;
import org.profit.candle.proto.trading.v1.ProcessTodayCloseReservationsRequest;
import org.profit.candle.proto.trading.v1.ProcessTodayCloseReservationsResponse;
import org.profit.candle.proto.trading.v1.ReservationServiceGrpc;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

class GrpcTradingBatchClientTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 6);

    @Test
    void mapsAllTradingBatchOperations() throws Exception {
        RecordingReservationService reservations = new RecordingReservationService();
        RecordingOrderService orders = new RecordingOrderService();

        try (TestServer server = startServer(reservations, orders)) {
            TradingBatchClient client = new GrpcTradingBatchClient(
                    channelFactory(server.channel()),
                    properties()
            );

            assertThat(client.processPreviousCloseReservations(DATE)).isEqualTo(1);
            assertThat(client.processOpenLimitReservations(DATE)).isEqualTo(2);
            assertThat(client.expirePendingOrders()).isEqualTo(3);
            assertThat(client.listStaleConvertingReservations(DATE))
                    .containsExactly("stale-1", "stale-2");
            assertThat(client.failStaleConvertingReservation("stale-1")).isTrue();
            assertThat(client.processTodayCloseReservations(DATE)).isEqualTo(4);
            assertThat(client.listExpirableReservations(DATE))
                    .containsExactly("expire-1", "expire-2");
            assertThat(client.expireReservation("expire-1")).isTrue();
            assertThat(reservations.scheduledDates).containsOnly(DATE.toString());
            assertThat(reservations.reservationIds)
                    .containsExactly("stale-1", "expire-1");
        }
    }

    @Test
    void mapsInternalErrorAsRetryable() throws Exception {
        try (TestServer server = startServer(
                failingReservationService(Status.INTERNAL),
                new RecordingOrderService()
        )) {
            TradingBatchClient client = new GrpcTradingBatchClient(
                    channelFactory(server.channel()),
                    properties()
            );

            assertThatThrownBy(() -> client.processOpenLimitReservations(DATE))
                    .isInstanceOf(TradingBatchException.class)
                    .satisfies(exception -> assertThat(
                            ((TradingBatchException) exception).retryable()
                    ).isTrue());
        }
    }

    @Test
    void mapsInvalidArgumentAsNonRetryable() throws Exception {
        try (TestServer server = startServer(
                failingReservationService(Status.INVALID_ARGUMENT),
                new RecordingOrderService()
        )) {
            TradingBatchClient client = new GrpcTradingBatchClient(
                    channelFactory(server.channel()),
                    properties()
            );

            assertThatThrownBy(() -> client.processOpenLimitReservations(DATE))
                    .isInstanceOf(TradingBatchException.class)
                    .satisfies(exception -> assertThat(
                            ((TradingBatchException) exception).retryable()
                    ).isFalse());
        }
    }

    private TestServer startServer(
            ReservationServiceGrpc.ReservationServiceImplBase reservations,
            OrderServiceGrpc.OrderServiceImplBase orders
    ) throws Exception {
        String name = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(reservations)
                .addService(orders)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build();
        return new TestServer(server, channel);
    }

    private ReservationServiceGrpc.ReservationServiceImplBase failingReservationService(
            Status status
    ) {
        return new ReservationServiceGrpc.ReservationServiceImplBase() {
            @Override
            public void processOpenLimitReservations(
                    ProcessOpenLimitReservationsRequest request,
                    StreamObserver<ProcessOpenLimitReservationsResponse> observer
            ) {
                observer.onError(status.asRuntimeException());
            }
        };
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

    private static final class RecordingReservationService
            extends ReservationServiceGrpc.ReservationServiceImplBase {

        private final List<String> scheduledDates = new ArrayList<>();
        private final List<String> reservationIds = new ArrayList<>();

        @Override
        public void processPrevCloseReservations(
                ProcessPrevCloseReservationsRequest request,
                StreamObserver<ProcessPrevCloseReservationsResponse> observer
        ) {
            scheduledDates.add(request.getScheduledDate());
            observer.onNext(ProcessPrevCloseReservationsResponse.newBuilder()
                    .setProcessedCount(1).build());
            observer.onCompleted();
        }

        @Override
        public void processOpenLimitReservations(
                ProcessOpenLimitReservationsRequest request,
                StreamObserver<ProcessOpenLimitReservationsResponse> observer
        ) {
            scheduledDates.add(request.getScheduledDate());
            observer.onNext(ProcessOpenLimitReservationsResponse.newBuilder()
                    .setProcessedCount(2).build());
            observer.onCompleted();
        }

        @Override
        public void listStaleConvertingReservations(
                ListStaleConvertingReservationsRequest request,
                StreamObserver<ListStaleConvertingReservationsResponse> observer
        ) {
            scheduledDates.add(request.getScheduledDate());
            observer.onNext(ListStaleConvertingReservationsResponse.newBuilder()
                    .addReservationIds("stale-1")
                    .addReservationIds("stale-2")
                    .build());
            observer.onCompleted();
        }

        @Override
        public void failStaleConvertingReservation(
                FailStaleConvertingReservationRequest request,
                StreamObserver<FailStaleConvertingReservationResponse> observer
        ) {
            reservationIds.add(request.getReservationId());
            observer.onNext(FailStaleConvertingReservationResponse.newBuilder()
                    .setFailed(true).build());
            observer.onCompleted();
        }

        @Override
        public void processTodayCloseReservations(
                ProcessTodayCloseReservationsRequest request,
                StreamObserver<ProcessTodayCloseReservationsResponse> observer
        ) {
            scheduledDates.add(request.getScheduledDate());
            observer.onNext(ProcessTodayCloseReservationsResponse.newBuilder()
                    .setProcessedCount(4).build());
            observer.onCompleted();
        }

        @Override
        public void listExpirableReservations(
                ListExpirableReservationsRequest request,
                StreamObserver<ListExpirableReservationsResponse> observer
        ) {
            scheduledDates.add(request.getScheduledDate());
            observer.onNext(ListExpirableReservationsResponse.newBuilder()
                    .addReservationIds("expire-1")
                    .addReservationIds("expire-2")
                    .build());
            observer.onCompleted();
        }

        @Override
        public void expireReservation(
                ExpireReservationRequest request,
                StreamObserver<ExpireReservationResponse> observer
        ) {
            reservationIds.add(request.getReservationId());
            observer.onNext(ExpireReservationResponse.newBuilder()
                    .setExpired(true).build());
            observer.onCompleted();
        }
    }

    private static final class RecordingOrderService
            extends OrderServiceGrpc.OrderServiceImplBase {

        @Override
        public void expirePendingOrders(
                ExpirePendingOrdersRequest request,
                StreamObserver<ExpirePendingOrdersResponse> observer
        ) {
            observer.onNext(ExpirePendingOrdersResponse.newBuilder()
                    .setCancelledCount(3).build());
            observer.onCompleted();
        }
    }

    private record TestServer(Server server, ManagedChannel channel) implements AutoCloseable {

        @Override
        public void close() throws InterruptedException {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}

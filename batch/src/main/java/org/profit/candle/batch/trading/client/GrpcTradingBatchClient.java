package org.profit.candle.batch.trading.client;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.trading.exception.TradingBatchErrorCode;
import org.profit.candle.batch.trading.exception.TradingBatchException;
import org.profit.candle.proto.trading.v1.ExpirePendingOrdersRequest;
import org.profit.candle.proto.trading.v1.ExpireReservationRequest;
import org.profit.candle.proto.trading.v1.FailStaleConvertingReservationRequest;
import org.profit.candle.proto.trading.v1.ListExpirableReservationsRequest;
import org.profit.candle.proto.trading.v1.ListStaleConvertingReservationsRequest;
import org.profit.candle.proto.trading.v1.OrderServiceGrpc;
import org.profit.candle.proto.trading.v1.ProcessOpenLimitReservationsRequest;
import org.profit.candle.proto.trading.v1.ProcessPrevCloseReservationsRequest;
import org.profit.candle.proto.trading.v1.ProcessTodayCloseReservationsRequest;
import org.profit.candle.proto.trading.v1.ReservationServiceGrpc;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Component
public class GrpcTradingBatchClient implements TradingBatchClient {

    private final ReservationServiceGrpc.ReservationServiceBlockingStub reservationStub;
    private final OrderServiceGrpc.OrderServiceBlockingStub orderStub;
    private final long deadlineMillis;

    public GrpcTradingBatchClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties
    ) {
        Channel channel = channelFactory.createChannel(batchProperties.grpc().tradingTarget());
        this.reservationStub = ReservationServiceGrpc.newBlockingStub(channel);
        this.orderStub = OrderServiceGrpc.newBlockingStub(channel);
        this.deadlineMillis = batchProperties.grpc().tradingBatchDeadlineMillis();
    }

    @Override
    public int processPreviousCloseReservations(LocalDate scheduledDate) {
        try {
            return reservationStub()
                    .processPrevCloseReservations(ProcessPrevCloseReservationsRequest.newBuilder()
                            .setScheduledDate(scheduledDate.toString())
                            .build())
                    .getProcessedCount();
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    @Override
    public int processOpenLimitReservations(LocalDate scheduledDate) {
        try {
            return reservationStub()
                    .processOpenLimitReservations(ProcessOpenLimitReservationsRequest.newBuilder()
                            .setScheduledDate(scheduledDate.toString())
                            .build())
                    .getProcessedCount();
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    @Override
    public int expirePendingOrders() {
        try {
            return orderStub()
                    .expirePendingOrders(ExpirePendingOrdersRequest.getDefaultInstance())
                    .getCancelledCount();
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    @Override
    public List<String> listStaleConvertingReservations(LocalDate scheduledDate) {
        try {
            return List.copyOf(reservationStub()
                    .listStaleConvertingReservations(
                            ListStaleConvertingReservationsRequest.newBuilder()
                                    .setScheduledDate(scheduledDate.toString())
                                    .build()
                    )
                    .getReservationIdsList());
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    @Override
    public boolean failStaleConvertingReservation(String reservationId) {
        try {
            return reservationStub()
                    .failStaleConvertingReservation(
                            FailStaleConvertingReservationRequest.newBuilder()
                                    .setReservationId(reservationId)
                                    .build()
                    )
                    .getFailed();
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    @Override
    public int processTodayCloseReservations(LocalDate scheduledDate) {
        try {
            return reservationStub()
                    .processTodayCloseReservations(ProcessTodayCloseReservationsRequest.newBuilder()
                            .setScheduledDate(scheduledDate.toString())
                            .build())
                    .getProcessedCount();
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    @Override
    public List<String> listExpirableReservations(LocalDate scheduledDate) {
        try {
            return List.copyOf(reservationStub()
                    .listExpirableReservations(ListExpirableReservationsRequest.newBuilder()
                            .setScheduledDate(scheduledDate.toString())
                            .build())
                    .getReservationIdsList());
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    @Override
    public boolean expireReservation(String reservationId) {
        try {
            return reservationStub()
                    .expireReservation(ExpireReservationRequest.newBuilder()
                            .setReservationId(reservationId)
                            .build())
                    .getExpired();
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    private ReservationServiceGrpc.ReservationServiceBlockingStub reservationStub() {
        return reservationStub.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS);
    }

    private OrderServiceGrpc.OrderServiceBlockingStub orderStub() {
        return orderStub.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS);
    }

    private TradingBatchException mapException(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        boolean retryable = code == Status.Code.INTERNAL
                || code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.RESOURCE_EXHAUSTED
                || code == Status.Code.ABORTED;
        TradingBatchErrorCode errorCode = retryable
                ? TradingBatchErrorCode.EXTERNAL_CLIENT_RETRYABLE
                : TradingBatchErrorCode.EXTERNAL_CLIENT_FAILED;
        return new TradingBatchException(errorCode, exception);
    }
}

package org.profit.candle.batch.trading.client;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.trading.exception.TradingBatchErrorCode;
import org.profit.candle.batch.trading.exception.TradingBatchException;
import org.profit.candle.proto.stock.v1.ChartServiceGrpc;
import org.profit.candle.proto.stock.v1.CloseDailyCandlesRequest;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Component
public class GrpcDailyCandleCloseClient implements DailyCandleCloseClient {

    private static final Metadata.Key<String> ROLE = Metadata.Key.of(
            "x-role",
            Metadata.ASCII_STRING_MARSHALLER
    );
    private static final Metadata.Key<String> IDEMPOTENCY_KEY = Metadata.Key.of(
            "x-idempotency-key",
            Metadata.ASCII_STRING_MARSHALLER
    );

    private final ChartServiceGrpc.ChartServiceBlockingStub stub;
    private final long deadlineMillis;

    public GrpcDailyCandleCloseClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties
    ) {
        this.stub = ChartServiceGrpc.newBlockingStub(
                channelFactory.createChannel(batchProperties.grpc().stockTarget())
        );
        this.deadlineMillis = batchProperties.grpc().tradingBatchDeadlineMillis();
    }

    @Override
    public int close(LocalDate tradeDate, String idempotencyKey) {
        try {
            Metadata metadata = new Metadata();
            metadata.put(ROLE, "SYSTEM");
            metadata.put(IDEMPOTENCY_KEY, idempotencyKey);
            return stub
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                    .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .closeDailyCandles(CloseDailyCandlesRequest.newBuilder()
                            .setTradeDate(tradeDate.toString())
                            .setIdempotencyKey(idempotencyKey)
                            .build())
                    .getClosedCount();
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    private TradingBatchException mapException(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        boolean retryable = code == Status.Code.INTERNAL
                || code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.RESOURCE_EXHAUSTED
                || code == Status.Code.ABORTED;
        TradingBatchErrorCode errorCode = retryable
                ? TradingBatchErrorCode.STOCK_CLIENT_RETRYABLE
                : TradingBatchErrorCode.STOCK_CLIENT_FAILED;
        return new TradingBatchException(errorCode, exception);
    }
}

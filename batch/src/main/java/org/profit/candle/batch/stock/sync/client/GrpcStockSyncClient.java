package org.profit.candle.batch.stock.sync.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.stock.sync.exception.StockSyncErrorCode;
import org.profit.candle.batch.stock.sync.exception.StockSyncException;
import org.profit.candle.proto.stock.v1.MarketType;
import org.profit.candle.proto.stock.v1.StockServiceGrpc;
import org.profit.candle.proto.stock.v1.SyncStocksRequest;
import org.profit.candle.proto.stock.v1.SyncStocksResponse;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Component
public class GrpcStockSyncClient implements StockSyncClient {

    private final StockServiceGrpc.StockServiceBlockingStub stub;
    private final long deadlineMillis;

    public GrpcStockSyncClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties
    ) {
        this.stub = StockServiceGrpc.newBlockingStub(
                channelFactory.createChannel(batchProperties.grpc().stockTarget())
        );
        this.deadlineMillis = batchProperties.grpc().stockSyncDeadlineMillis();
    }

    @Override
    public Result sync(Market market) {
        try {
            SyncStocksResponse response = stub
                    .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .syncStocks(SyncStocksRequest.newBuilder()
                            .setMarket(toProto(market))
                            .build());
            return new Result(response.getUpserted(), response.getTotal());
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    private MarketType toProto(Market market) {
        return switch (market) {
            case KOSPI -> MarketType.KOSPI;
            case KOSDAQ -> MarketType.KOSDAQ;
        };
    }

    private StockSyncException mapException(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        boolean retryable = code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.RESOURCE_EXHAUSTED
                || code == Status.Code.ABORTED
                || code == Status.Code.INTERNAL;
        StockSyncErrorCode errorCode = retryable
                ? StockSyncErrorCode.EXTERNAL_CLIENT_RETRYABLE
                : StockSyncErrorCode.EXTERNAL_CLIENT_FAILED;
        return new StockSyncException(errorCode, exception);
    }
}

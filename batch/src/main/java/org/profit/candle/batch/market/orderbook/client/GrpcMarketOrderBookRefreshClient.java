package org.profit.candle.batch.market.orderbook.client;

import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.proto.market.v1.GetMarketStatusRequest;
import org.profit.candle.proto.market.v1.MarketServiceGrpc;
import org.profit.candle.proto.market.v1.RefreshOrderBooksRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class GrpcMarketOrderBookRefreshClient implements MarketOrderBookRefreshClient {
    private final MarketServiceGrpc.MarketServiceBlockingStub stub;
    private final long deadlineMillis;

    public GrpcMarketOrderBookRefreshClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties,
            @Value("${batch.schedule.market-order-book.deadline-millis:120000}") long deadlineMillis
    ) {
        Channel channel = channelFactory.createChannel(batchProperties.grpc().marketTarget());
        this.stub = MarketServiceGrpc.newBlockingStub(channel);
        this.deadlineMillis = deadlineMillis;
    }

    @Override
    public boolean isMarketOpen() {
        try {
            return stub.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .getMarketStatus(GetMarketStatusRequest.getDefaultInstance())
                    .getOpen();
        } catch (StatusRuntimeException e) {
            return false;
        }
    }

    @Override
    public Result refresh() {
        var response = stub.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .refreshOrderBooks(RefreshOrderBooksRequest.getDefaultInstance());
        return new Result(
                response.getTargetCount(),
                response.getSuccessCount(),
                response.getFailCount(),
                response.getSkipped(),
                response.getReason()
        );
    }
}

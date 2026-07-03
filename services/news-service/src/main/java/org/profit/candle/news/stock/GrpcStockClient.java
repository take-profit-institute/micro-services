package org.profit.candle.news.stock;

import org.profit.candle.proto.stock.v1.GetStockRequest;
import org.profit.candle.proto.stock.v1.GetStockResponse;
import org.profit.candle.proto.stock.v1.StockServiceGrpc;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class GrpcStockClient implements StockClient {
    private final StockServiceGrpc.StockServiceBlockingStub stub;
    private final StockGrpcProperties properties;

    public GrpcStockClient(GrpcChannelFactory channelFactory, StockGrpcProperties properties) {
        this.properties = properties;
        this.stub = StockServiceGrpc.newBlockingStub(
                channelFactory.createChannel("stock-service"));
    }

    @Override
    public StockSnapshot getStock(String code) {
        GetStockRequest request = GetStockRequest.newBuilder()
                .setCode(code)
                .setAllowFallback(false)
                .build();
        GetStockResponse response = stub
                .withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS)
                .getStock(request);
        return StockSnapshotMapper.fromProto(response.getStock());
    }
}

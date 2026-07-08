package org.profit.candle.news.stock;

import org.profit.candle.proto.stock.v1.GetStockRequest;
import org.profit.candle.proto.stock.v1.GetStockResponse;
import org.profit.candle.proto.stock.v1.ListingStatus;
import org.profit.candle.proto.stock.v1.SearchStocksRequest;
import org.profit.candle.proto.stock.v1.SearchStocksResponse;
import org.profit.candle.proto.stock.v1.StockServiceGrpc;
import org.profit.candle.proto.stock.v1.StockSort;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
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

    @Override
    public StockSearchPage listListedStocks(int page, int size) {
        SearchStocksRequest request = SearchStocksRequest.newBuilder()
                .setStatus(ListingStatus.LISTED)
                .setSort(StockSort.CODE_ASC)
                .setPage(page)
                .setSize(size)
                .build();
        SearchStocksResponse response = stub
                .withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS)
                .searchStocks(request);
        List<StockSnapshot> stocks = response.getStocksList().stream()
                .map(StockSnapshotMapper::fromProto)
                .toList();
        return new StockSearchPage(
                stocks,
                response.getTotalElements(),
                response.getTotalPages(),
                response.getPage(),
                response.getSize()
        );
    }
}

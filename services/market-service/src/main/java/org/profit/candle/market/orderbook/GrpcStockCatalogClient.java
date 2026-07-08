package org.profit.candle.market.orderbook;

import io.grpc.Deadline;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.proto.stock.v1.ListingStatus;
import org.profit.candle.proto.stock.v1.SearchStocksRequest;
import org.profit.candle.proto.stock.v1.SearchStocksResponse;
import org.profit.candle.proto.stock.v1.StockServiceGrpc;
import org.profit.candle.proto.stock.v1.StockSort;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GrpcStockCatalogClient implements StockCatalogClient {
    private static final int PAGE_SIZE = 100;
    private static final int DEADLINE_SECONDS = 10;

    private final StockServiceGrpc.StockServiceBlockingStub stub;

    public GrpcStockCatalogClient(GrpcChannelFactory channelFactory) {
        this.stub = StockServiceGrpc.newBlockingStub(channelFactory.createChannel("stock-service"));
    }

    @Override
    public List<String> listListedStockCodes() {
        List<String> codes = new ArrayList<>();
        int page = 0;
        int totalPages = 1;

        while (page < totalPages) {
            SearchStocksResponse response = stub
                    .withDeadline(Deadline.after(DEADLINE_SECONDS, TimeUnit.SECONDS))
                    .searchStocks(SearchStocksRequest.newBuilder()
                            .setStatus(ListingStatus.LISTED)
                            .setSort(StockSort.CODE_ASC)
                            .setPage(page)
                            .setSize(PAGE_SIZE)
                            .build());

            response.getStocksList().stream()
                    .map(org.profit.candle.proto.stock.v1.Stock::getCode)
                    .filter(code -> code != null && !code.isBlank())
                    .forEach(codes::add);

            totalPages = Math.max(1, response.getTotalPages());
            page++;
        }

        log.info("Listed stock codes loaded from stock-service. count={}", codes.size());
        return codes;
    }
}

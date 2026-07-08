package org.profit.candle.batch.stock.candle.client;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.profit.candle.batch.config.BatchProperties;
import org.profit.candle.batch.stock.candle.exception.StockCandleErrorCode;
import org.profit.candle.batch.stock.candle.exception.StockCandleException;
import org.profit.candle.proto.stock.v1.ListingStatus;
import org.profit.candle.proto.stock.v1.SearchStocksRequest;
import org.profit.candle.proto.stock.v1.SearchStocksResponse;
import org.profit.candle.proto.stock.v1.Stock;
import org.profit.candle.proto.stock.v1.StockServiceGrpc;
import org.profit.candle.proto.stock.v1.StockSort;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Component
public class GrpcStockCatalogClient implements StockCatalogClient {

    private static final Metadata.Key<String> ROLE = Metadata.Key.of(
            "x-role",
            Metadata.ASCII_STRING_MARSHALLER
    );

    private final StockServiceGrpc.StockServiceBlockingStub stub;
    private final long deadlineMillis;

    public GrpcStockCatalogClient(
            GrpcChannelFactory channelFactory,
            BatchProperties batchProperties
    ) {
        this.stub = StockServiceGrpc.newBlockingStub(
                channelFactory.createChannel(batchProperties.grpc().stockTarget())
        );
        // 카탈로그 검색은 stock-service DB 조회지만, sync와 동일 버킷의 넉넉한 deadline을 쓴다.
        this.deadlineMillis = batchProperties.grpc().stockSyncDeadlineMillis();
    }

    @Override
    public Page listListedCodes(int page, int size) {
        try {
            Metadata metadata = new Metadata();
            metadata.put(ROLE, "SYSTEM");
            SearchStocksResponse response = stub
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                    .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                    .searchStocks(SearchStocksRequest.newBuilder()
                            .setStatus(ListingStatus.LISTED)
                            .setSort(StockSort.CODE_ASC)
                            .setPage(page)
                            .setSize(size)
                            .build());
            List<String> codes = response.getStocksList().stream()
                    .map(Stock::getCode)
                    .toList();
            return new Page(codes, response.getTotalPages());
        } catch (StatusRuntimeException exception) {
            throw mapException(exception);
        }
    }

    private StockCandleException mapException(StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        boolean retryable = code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.RESOURCE_EXHAUSTED
                || code == Status.Code.ABORTED
                || code == Status.Code.INTERNAL;
        StockCandleErrorCode errorCode = retryable
                ? StockCandleErrorCode.EXTERNAL_CLIENT_RETRYABLE
                : StockCandleErrorCode.EXTERNAL_CLIENT_FAILED;
        return new StockCandleException(errorCode, exception);
    }
}

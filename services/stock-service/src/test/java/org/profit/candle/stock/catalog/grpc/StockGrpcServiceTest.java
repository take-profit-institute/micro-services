package org.profit.candle.stock.catalog.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.proto.stock.v1.BatchGetStocksRequest;
import org.profit.candle.proto.stock.v1.BatchGetStocksResponse;
import org.profit.candle.proto.stock.v1.DataSource;
import org.profit.candle.proto.stock.v1.GetStockRequest;
import org.profit.candle.proto.stock.v1.GetStockResponse;
import org.profit.candle.proto.stock.v1.ListingStatus;
import org.profit.candle.proto.stock.v1.MarketType;
import org.profit.candle.proto.stock.v1.SearchStocksRequest;
import org.profit.candle.proto.stock.v1.SearchStocksResponse;
import org.profit.candle.proto.stock.v1.StockSort;
import org.profit.candle.proto.stock.v1.SyncStocksRequest;
import org.profit.candle.proto.stock.v1.SyncStocksResponse;
import org.profit.candle.stock.catalog.dto.StockDataSource;
import org.profit.candle.stock.catalog.dto.StockDetailResult;
import org.profit.candle.stock.catalog.dto.StockResult;
import org.profit.candle.stock.catalog.dto.StockSearchCriteria;
import org.profit.candle.stock.catalog.exception.StockErrorCode;
import org.profit.candle.stock.catalog.service.StockCatalogService;
import org.profit.candle.stock.catalog.service.StockIngestionService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockGrpcServiceTest {

    @Mock StockCatalogService catalogService;
    @Mock StockIngestionService ingestionService;

    @Test
    void searchStocks_buildsCriteriaPageableAndReturnsStocks() {
        when(catalogService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(stock("005930", "삼성전자", "KOSPI")),
                        org.springframework.data.domain.PageRequest.of(0, 20), 1));
        StockGrpcService service = new StockGrpcService(catalogService, ingestionService);
        CapturingObserver<SearchStocksResponse> observer = new CapturingObserver<>();

        service.searchStocks(SearchStocksRequest.newBuilder()
                .setQuery("삼성")
                .setMarket(MarketType.KOSPI)
                .setSector("전기전자")
                .setStatus(ListingStatus.LISTING_STATUS_UNSPECIFIED)
                .setSort(StockSort.MARKET_CAP_DESC)
                .setPage(-1)
                .setSize(500)
                .build(), observer);

        ArgumentCaptor<StockSearchCriteria> criteriaCaptor = ArgumentCaptor.forClass(StockSearchCriteria.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(catalogService).search(criteriaCaptor.capture(), pageableCaptor.capture());
        assertThat(criteriaCaptor.getValue().query()).isEqualTo("삼성");
        assertThat(criteriaCaptor.getValue().market()).isEqualTo("KOSPI");
        assertThat(criteriaCaptor.getValue().sector()).isEqualTo("전기전자");
        assertThat(criteriaCaptor.getValue().status()).isEqualTo("LISTED");
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getSort().toString()).contains("marketCap: DESC", "stockCode: ASC");

        assertThat(observer.value.getStocksCount()).isEqualTo(1);
        assertThat(observer.value.getStocks(0).getCode()).isEqualTo("005930");
        assertThat(observer.completed).isTrue();
    }

    @Test
    void getStock_returnsDetailAndSource() {
        when(catalogService.getStock("005930", true))
                .thenReturn(new StockDetailResult(stock("005930", "삼성전자", "KOSPI"), null, StockDataSource.KIWOOM));
        StockGrpcService service = new StockGrpcService(catalogService, ingestionService);
        CapturingObserver<GetStockResponse> observer = new CapturingObserver<>();

        service.getStock(GetStockRequest.newBuilder().setCode("005930").setAllowFallback(true).build(), observer);

        assertThat(observer.value.getStock().getStock().getName()).isEqualTo("삼성전자");
        assertThat(observer.value.getSource()).isEqualTo(DataSource.KIWOOM);
        assertThat(observer.completed).isTrue();
    }

    @Test
    void getStock_stockNotFound_returnsNotFoundError() {
        when(catalogService.getStock("999999", false))
                .thenThrow(new CandleException(StockErrorCode.STOCK_NOT_FOUND));
        StockGrpcService service = new StockGrpcService(catalogService, ingestionService);
        CapturingObserver<GetStockResponse> observer = new CapturingObserver<>();

        service.getStock(GetStockRequest.newBuilder().setCode("999999").build(), observer);

        StatusRuntimeException error = (StatusRuntimeException) observer.error;
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(error.getStatus().getDescription()).isEqualTo("STOCK_NOT_FOUND");
    }

    @Test
    void batchGetStocks_returnsBatchItems() {
        when(catalogService.batchGet(List.of("005930")))
                .thenReturn(List.of(stock("005930", "삼성전자", "KOSPI")));
        StockGrpcService service = new StockGrpcService(catalogService, ingestionService);
        CapturingObserver<BatchGetStocksResponse> observer = new CapturingObserver<>();

        service.batchGetStocks(BatchGetStocksRequest.newBuilder().addCodes("005930").build(), observer);

        assertThat(observer.value.getStocksCount()).isEqualTo(1);
        assertThat(observer.value.getStocks(0).getName()).isEqualTo("삼성전자");
        assertThat(observer.completed).isTrue();
    }

    @Test
    void syncStocks_returnsUpsertedCount() {
        when(ingestionService.syncMarket("KOSDAQ")).thenReturn(7);
        StockGrpcService service = new StockGrpcService(catalogService, ingestionService);
        CapturingObserver<SyncStocksResponse> observer = new CapturingObserver<>();

        service.syncStocks(SyncStocksRequest.newBuilder().setMarket(MarketType.KOSDAQ).build(), observer);

        assertThat(observer.value.getUpserted()).isEqualTo(7);
        assertThat(observer.value.getTotal()).isEqualTo(7);
        assertThat(observer.completed).isTrue();
    }

    private StockResult stock(String code, String name, String marketType) {
        return new StockResult(code, name, marketType, "전기전자", 1_000L, 100L,
                LocalDate.of(2020, 1, 1), "LISTED", null, null);
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}

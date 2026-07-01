package org.profit.candle.stock.catalog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.stock.catalog.dto.StockDataSource;
import org.profit.candle.stock.catalog.dto.StockDetailResult;
import org.profit.candle.stock.catalog.dto.StockResult;
import org.profit.candle.stock.catalog.dto.StockSearchCriteria;
import org.profit.candle.stock.catalog.entity.StockEntity;
import org.profit.candle.stock.catalog.exception.StockErrorCode;
import org.profit.candle.stock.catalog.repository.StockFinancialsReader;
import org.profit.candle.stock.catalog.repository.StockReader;
import org.profit.candle.stock.config.KiwoomProperties;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultStockCatalogServiceTest {

    @Mock StockReader stockReader;
    @Mock StockFinancialsReader financialsReader;
    @Mock StockIngestionService ingestionService;

    private final KiwoomProperties kiwoomProperties =
            new KiwoomProperties(null, null, null, Duration.ofDays(7), null, null, null, null, null, null);

    @Test
    void search_delegatesCriteriaAndMapsResults() {
        StockEntity samsung = new StockEntity("005930", "삼성전자", "KOSPI");
        PageRequest pageable = PageRequest.of(0, 20);
        when(stockReader.search("삼성", "KOSPI", "전기전자", "LISTED", pageable))
                .thenReturn(new PageImpl<>(List.of(samsung), pageable, 1));
        DefaultStockCatalogService catalogService =
                new DefaultStockCatalogService(stockReader, financialsReader, ingestionService, kiwoomProperties);

        var result = catalogService.search(
                new StockSearchCriteria("삼성", "KOSPI", "전기전자", "LISTED"),
                pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().code()).isEqualTo("005930");
    }

    @Test
    void getStock_returnsDbResultWhenStockExistsAndNotStale() {
        StockEntity stock = syncedStock("005930", "삼성전자");
        when(stockReader.findByStockCode("005930")).thenReturn(Optional.of(stock));
        when(financialsReader.findLatestByStockId(stock.stockId())).thenReturn(Optional.empty());
        DefaultStockCatalogService catalogService =
                new DefaultStockCatalogService(stockReader, financialsReader, ingestionService, kiwoomProperties);

        StockDetailResult result = catalogService.getStock("005930", true);

        assertThat(result.stock().code()).isEqualTo("005930");
        assertThat(result.source()).isEqualTo(StockDataSource.DB);
        verify(ingestionService, never()).fetchAndSave("005930");
    }

    @Test
    void getStock_returnsKiwoomResultWhenDbMissAndFallbackSucceeds() {
        when(stockReader.findByStockCode("005930")).thenReturn(Optional.empty());
        when(ingestionService.fetchAndSave("005930"))
                .thenReturn(Optional.of(new StockResult("005930", "삼성전자", "KOSPI",
                        null, null, null, null, "LISTED", null, null)));
        DefaultStockCatalogService catalogService =
                new DefaultStockCatalogService(stockReader, financialsReader, ingestionService, kiwoomProperties);

        StockDetailResult result = catalogService.getStock("005930", true);

        assertThat(result.stock().name()).isEqualTo("삼성전자");
        assertThat(result.source()).isEqualTo(StockDataSource.KIWOOM);
        assertThat(result.financials()).isNull();
    }

    @Test
    void getStock_dbMissAndFallbackDisabled_throwsStockNotFound() {
        when(stockReader.findByStockCode("999999")).thenReturn(Optional.empty());
        DefaultStockCatalogService catalogService =
                new DefaultStockCatalogService(stockReader, financialsReader, ingestionService, kiwoomProperties);

        assertThatThrownBy(() -> catalogService.getStock("999999", false))
                .isInstanceOf(CandleException.class)
                .satisfies(e -> assertThat(((CandleException) e).errorCode())
                        .isEqualTo(StockErrorCode.STOCK_NOT_FOUND));
        verify(ingestionService, never()).fetchAndSave("999999");
    }

    @Test
    void batchGet_emptyInput_returnsEmptyListWithoutRepositoryCall() {
        DefaultStockCatalogService catalogService =
                new DefaultStockCatalogService(stockReader, financialsReader, ingestionService, kiwoomProperties);

        assertThat(catalogService.batchGet(List.of())).isEmpty();

        verify(stockReader, never()).findByStockCodeIn(List.of());
    }

    @Test
    void batchGet_mapsRepositoryResults() {
        when(stockReader.findByStockCodeIn(List.of("005930")))
                .thenReturn(List.of(new StockEntity("005930", "삼성전자", "KOSPI")));
        DefaultStockCatalogService catalogService =
                new DefaultStockCatalogService(stockReader, financialsReader, ingestionService, kiwoomProperties);

        List<StockResult> result = catalogService.batchGet(List.of("005930"));

        assertThat(result).extracting(StockResult::code).containsExactly("005930");
    }

    private StockEntity syncedStock(String code, String name) {
        StockEntity stock = new StockEntity(code, name, "KOSPI");
        stock.applyReferenceData(name, "KOSPI", null, null, null, null, "LISTED", "KIWOOM");
        return stock;
    }
}

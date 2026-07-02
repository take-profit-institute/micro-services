package org.profit.candle.stock.catalog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.stock.catalog.entity.StockEntity;
import org.profit.candle.stock.catalog.repository.StockReader;
import org.profit.candle.stock.catalog.repository.StockWriter;
import org.profit.candle.stock.client.KiwoomStockClient;
import org.profit.candle.stock.client.KiwoomStockData;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultStockIngestionServiceTest {

    @Mock KiwoomStockClient kiwoomStockClient;
    @Mock StockReader stockReader;
    @Mock StockWriter stockWriter;
    @InjectMocks DefaultStockIngestionService service;

    @Test
    void fetchAndSave_returnsEmptyWhenClientHasNoData() {
        when(kiwoomStockClient.findStock("005930")).thenReturn(Optional.empty());

        assertThat(service.fetchAndSave("005930")).isEmpty();

        verify(stockWriter, never()).save(any());
    }

    @Test
    void fetchAndSave_createsNewStockWhenMissing() {
        KiwoomStockData data = data("005930", "삼성전자", "KOSPI");
        when(kiwoomStockClient.findStock("005930")).thenReturn(Optional.of(data));
        when(stockReader.findByStockCode("005930")).thenReturn(Optional.empty());
        when(stockWriter.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.fetchAndSave("005930");

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("005930");
        assertThat(result.get().name()).isEqualTo("삼성전자");
        ArgumentCaptor<StockEntity> captor = ArgumentCaptor.forClass(StockEntity.class);
        verify(stockWriter).save(captor.capture());
        assertThat(captor.getValue().dataSource()).isEqualTo("KIWOOM");
        assertThat(captor.getValue().syncedAt()).isNotNull();
    }

    @Test
    void fetchAndSave_updatesExistingStock() {
        StockEntity existing = new StockEntity("005930", "삼성전자", "KOSPI");
        KiwoomStockData data = data("005930", "삼성전자보통주", "KOSPI");
        when(kiwoomStockClient.findStock("005930")).thenReturn(Optional.of(data));
        when(stockReader.findByStockCode("005930")).thenReturn(Optional.of(existing));
        when(stockWriter.save(existing)).thenReturn(existing);

        var result = service.fetchAndSave("005930");

        assertThat(result).isPresent();
        assertThat(existing.stockName()).isEqualTo("삼성전자보통주");
        verify(stockWriter).save(existing);
    }

    @Test
    void fetchAndSave_recoversFromConcurrentInsertConflict() {
        KiwoomStockData data = data("005930", "삼성전자", "KOSPI");
        StockEntity existing = new StockEntity("005930", "기존명", "KOSPI");
        when(kiwoomStockClient.findStock("005930")).thenReturn(Optional.of(data));
        when(stockReader.findByStockCode("005930"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(stockWriter.save(any(StockEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate stock_code"))
                .thenReturn(existing);

        var result = service.fetchAndSave("005930");

        assertThat(result).isPresent();
        assertThat(existing.stockName()).isEqualTo("삼성전자");
        verify(stockWriter, times(2)).save(any(StockEntity.class));
    }

    @Test
    void syncMarket_upsertsAllClientStocksAndReturnsCount() {
        when(kiwoomStockClient.findAllStocksByMarket("KOSPI"))
                .thenReturn(List.of(data("005930", "삼성전자", "KOSPI"), data("000660", "SK하이닉스", "KOSPI")));
        when(stockReader.findByStockCode(any())).thenReturn(Optional.empty());
        when(stockWriter.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int count = service.syncMarket("KOSPI");

        assertThat(count).isEqualTo(2);
        verify(stockWriter, times(2)).save(any(StockEntity.class));
    }

    private KiwoomStockData data(String code, String name, String marketType) {
        return new KiwoomStockData(code, name, marketType, "전기전자", 1_000L, 100L,
                LocalDate.of(2020, 1, 1), "LISTED");
    }
}

package org.profit.candle.news.target.service;

import org.junit.jupiter.api.Test;
import org.profit.candle.news.stock.StockClient;
import org.profit.candle.news.stock.StockSearchPage;
import org.profit.candle.news.stock.StockSnapshot;
import org.profit.candle.news.target.repository.CollectionTargetJpaRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionTargetSyncServiceTest {
    @Test
    void shouldUpsertListedStocksByPage() {
        StockClient stockClient = mock(StockClient.class);
        CollectionTargetJpaRepository targetRepository = mock(CollectionTargetJpaRepository.class);
        when(stockClient.listListedStocks(0, 100)).thenReturn(new StockSearchPage(
                List.of(stock("005930"), stock("000660")),
                3,
                2,
                0,
                100
        ));
        when(stockClient.listListedStocks(1, 100)).thenReturn(new StockSearchPage(
                List.of(stock("035420")),
                3,
                2,
                1,
                100
        ));
        CollectionTargetSyncService service = new CollectionTargetSyncService(stockClient, targetRepository);

        CollectionTargetSyncResult result = service.syncListedStocksAsAdminTargets();

        assertThat(result.syncedCount()).isEqualTo(3);
        assertThat(result.pageCount()).isEqualTo(2);
        verify(targetRepository).upsertListedAdminTarget("005930", 1_000);
        verify(targetRepository).upsertListedAdminTarget("000660", 1_000);
        verify(targetRepository).upsertListedAdminTarget("035420", 1_000);
    }

    @Test
    void shouldStopWhenFirstPageIsEmpty() {
        StockClient stockClient = mock(StockClient.class);
        CollectionTargetJpaRepository targetRepository = mock(CollectionTargetJpaRepository.class);
        when(stockClient.listListedStocks(0, 100)).thenReturn(new StockSearchPage(
                List.of(),
                0,
                0,
                0,
                100
        ));
        CollectionTargetSyncService service = new CollectionTargetSyncService(stockClient, targetRepository);

        CollectionTargetSyncResult result = service.syncListedStocksAsAdminTargets();

        assertThat(result.syncedCount()).isZero();
        assertThat(result.pageCount()).isEqualTo(1);
        verify(targetRepository, never()).upsertListedAdminTarget(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static StockSnapshot stock(String code) {
        return new StockSnapshot(code, code + " name", "KOSPI", "", 0L, 0L, "LISTED");
    }
}

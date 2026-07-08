package org.profit.candle.news.target.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.news.stock.StockClient;
import org.profit.candle.news.stock.StockSearchPage;
import org.profit.candle.news.stock.StockSnapshot;
import org.profit.candle.news.target.repository.CollectionTargetJpaRepository;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionTargetSyncService {
    private static final int PAGE_SIZE = 100;
    private static final int LISTED_TARGET_PRIORITY = 1_000;

    private final StockClient stockClient;
    private final CollectionTargetJpaRepository targetRepository;

    public CollectionTargetSyncResult syncListedStocksAsAdminTargets() {
        int page = 0;
        int syncedCount = 0;
        int pageCount = 0;

        while (true) {
            StockSearchPage stocks = stockClient.listListedStocks(page, PAGE_SIZE);
            pageCount++;
            if (stocks.stocks().isEmpty()) {
                break;
            }

            for (StockSnapshot stock : stocks.stocks()) {
                if (stock.code() == null || stock.code().isBlank()) {
                    continue;
                }
                targetRepository.upsertListedAdminTarget(stock.code(), LISTED_TARGET_PRIORITY);
                syncedCount++;
            }

            if (page + 1 >= stocks.totalPages()) {
                break;
            }
            page++;
        }

        log.info("Listed stocks synced to news collection targets. syncedCount={}, pageCount={}",
                syncedCount, pageCount);
        return new CollectionTargetSyncResult(syncedCount, pageCount);
    }
}

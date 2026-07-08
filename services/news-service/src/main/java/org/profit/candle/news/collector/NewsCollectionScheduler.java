package org.profit.candle.news.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.news.target.service.CollectionTargetSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "news.collection.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NewsCollectionScheduler {
    private final NewsCollectionService newsCollectionService;
    private final CollectionTargetSyncService collectionTargetSyncService;
    private final NewsCollectionLock newsCollectionLock;

    @Scheduled(cron = "0 0 9,12,15 * * MON-FRI", zone = "Asia/Seoul")
    public void collectNews() {
        long startedAt = System.currentTimeMillis();
        log.info("=== News collection scheduler started ===");
        try (NewsCollectionLock.Handle ignored = newsCollectionLock.tryLock().orElse(null)) {
            if (ignored == null) {
                log.info("Scheduled news collection skipped because another instance holds the lock");
                return;
            }
            syncListedStocks();
            NewsCollectionResult result = newsCollectionService.collectActiveTargets();
            log.info(
                    "News collection completed. targetCount={}, successCount={}, failCount={}",
                    result.targetCount(),
                    result.successCount(),
                    result.failCount()
            );
        } catch (RuntimeException e) {
            log.error("Scheduled news collection failed", e);
        } finally {
            log.info("News collection scheduler finished. elapsed={}ms", System.currentTimeMillis() - startedAt);
        }
    }

    private void syncListedStocks() {
        try {
            collectionTargetSyncService.syncListedStocksAsAdminTargets();
        } catch (RuntimeException e) {
            log.error("Listed stock sync failed. Continuing news collection with existing targets (may be empty)", e);
        }
    }
}

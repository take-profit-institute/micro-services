package org.profit.candle.market.ranking.scheduler;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.ranking.service.StockRankingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockRankingScheduler {
    private final StockRankingService stockRankingService;

    @Scheduled(fixedDelay = 60000)
    public void refreshStockRanking() {
        stockRankingService.refreshRisingRanking();
        stockRankingService.refreshFallingRanking();
    }
}

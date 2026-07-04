package org.profit.candle.market.ranking.scheduler;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.ranking.service.FallingRankingService;
import org.profit.candle.market.ranking.service.PopularRankingService;
import org.profit.candle.market.ranking.service.RisingRankingService;
import org.profit.candle.market.ranking.service.VolumeSpikeRankingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockRankingScheduler { ;
    private final RisingRankingService risingRankingService;
    private final FallingRankingService fallingRankingService;
    private final VolumeSpikeRankingService volumeSpikeRankingService;
    private final PopularRankingService popularRankingService;

    @Scheduled(fixedDelay = 60000)
    public void refreshRisingRanking() {
        risingRankingService.refresh();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshFallingRanking() {
        fallingRankingService.refresh();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshVolumeSpikeRanking() {
        volumeSpikeRankingService.refresh();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshPopularRanking() {
        popularRankingService.refresh();
    }
}

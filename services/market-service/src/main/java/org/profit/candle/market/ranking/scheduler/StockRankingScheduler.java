package org.profit.candle.market.ranking.scheduler;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.ranking.service.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockRankingScheduler { ;
    private final RisingRankingService risingRankingService;
    private final FallingRankingService fallingRankingService;
    private final VolumeSpikeRankingService volumeSpikeRankingService;
    private final PopularRankingService popularRankingService;
    private final RateRankingService rateRankingService;

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

    @Scheduled(fixedDelay = 60000)
    public void refreshRateUpRanking() {
        rateRankingService.refreshRateUp();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshRateDownRanking() {
        rateRankingService.refreshRateDown();
    }
}

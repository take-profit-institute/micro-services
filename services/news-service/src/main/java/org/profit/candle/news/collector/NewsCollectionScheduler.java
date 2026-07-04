package org.profit.candle.news.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsCollectionScheduler {
    private final NewsCollectionService newsCollectionService;

    @Scheduled(cron = "0 0 9,12,15 * * MON-FRI", zone = "Asia/Seoul")
    public void collectNews() {
        try {
            newsCollectionService.collectActiveTargets();
        } catch (RuntimeException e) {
            log.error("Scheduled news collection failed", e);
        }
    }
}

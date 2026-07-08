package org.profit.candle.portfolio.holding.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldingMetadataBackfillRunner {
    private final HoldingMetadataBackfillService backfillService;

    @Value("${portfolio.holding.metadata-backfill.enabled:false}")
    private boolean enabled;
    @Value("${portfolio.holding.metadata-backfill.batch-size:200}")
    private int batchSize;
    @Value("${portfolio.holding.metadata-backfill.max-batches:50}")
    private int maxBatches;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        if (!enabled) {
            return;
        }

        int totalUpdated = 0;
        for (int i = 0; i < maxBatches; i++) {
            int updated = backfillService.backfill(batchSize);
            totalUpdated += updated;
            if (updated == 0) {
                break;
            }
        }
        log.info("Holding metadata backfill finished. updated={}", totalUpdated);
    }
}

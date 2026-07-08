package org.profit.candle.news.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.news.log.repository.CollectionLogJpaRepository;
import org.profit.candle.news.target.service.CollectionTargetSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "news.collection.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class NewsCollectionCatchUpRunner {
    private static final List<LocalTime> SLOTS = List.of(
            LocalTime.of(9, 0),
            LocalTime.of(12, 0),
            LocalTime.of(15, 0)
    );

    private final NewsCollectionService newsCollectionService;
    private final CollectionTargetSyncService collectionTargetSyncService;
    private final NewsCollectionLock newsCollectionLock;
    private final CollectionLogJpaRepository collectionLogRepository;
    private final Clock newsCollectionClock;

    @EventListener(ApplicationReadyEvent.class)
    public void runCatchUp() {
        LocalDateTime now = LocalDateTime.now(newsCollectionClock);
        if (isWeekend(now.toLocalDate())) {
            log.info("News collection catch-up skipped because today is not a weekday");
            return;
        }

        List<Slot> missedSlots = missedSlots(now);
        if (missedSlots.isEmpty()) {
            log.info("News collection catch-up skipped because there is no missed slot");
            return;
        }

        try (NewsCollectionLock.Handle ignored = newsCollectionLock.tryLock().orElse(null)) {
            if (ignored == null) {
                log.info("News collection catch-up skipped because another instance holds the lock");
                return;
            }
            syncListedStocks();
            for (Slot slot : missedSlots) {
                collectSlot(slot);
            }
        } catch (RuntimeException e) {
            log.error("News collection catch-up failed", e);
        }
    }

    private List<Slot> missedSlots(LocalDateTime now) {
        List<Slot> missedSlots = new ArrayList<>();
        for (int index = 0; index < SLOTS.size(); index++) {
            LocalDateTime slotTime = LocalDateTime.of(now.toLocalDate(), SLOTS.get(index));
            if (!slotTime.isBefore(now)) {
                continue;
            }
            Slot slot = new Slot(slotTime, nextSlotTime(now.toLocalDate(), index));
            if (!isCompleted(slot)) {
                missedSlots.add(slot);
            }
        }
        return missedSlots;
    }

    private LocalDateTime nextSlotTime(LocalDate date, int slotIndex) {
        if (slotIndex + 1 < SLOTS.size()) {
            return LocalDateTime.of(date, SLOTS.get(slotIndex + 1));
        }
        return date.plusDays(1).atStartOfDay();
    }

    private boolean isCompleted(Slot slot) {
        return collectionLogRepository.existsByMessageContaining(slot.marker())
                || collectionLogRepository.existsRegularCollectionBetween(toInstant(slot.startedAt()), toInstant(slot.finishedAt()));
    }

    private void collectSlot(Slot slot) {
        long startedAt = System.currentTimeMillis();
        log.info("News collection catch-up started. slot={}", slot.key());
        try {
            NewsCollectionResult result = newsCollectionService.collectActiveTargets("catchUpSlot=" + slot.key());
            log.info(
                    "News collection catch-up completed. slot={}, targetCount={}, successCount={}, failCount={}",
                    slot.key(),
                    result.targetCount(),
                    result.successCount(),
                    result.failCount()
            );
        } finally {
            log.info("News collection catch-up finished. slot={}, elapsed={}ms", slot.key(), System.currentTimeMillis() - startedAt);
        }
    }

    private void syncListedStocks() {
        try {
            collectionTargetSyncService.syncListedStocksAsAdminTargets();
        } catch (RuntimeException e) {
            log.error("Listed stock sync failed. Continuing news collection catch-up with existing targets (may be empty)", e);
        }
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return ZonedDateTime.of(dateTime, NewsCollectionClockConfig.NEWS_ZONE).toInstant();
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private record Slot(LocalDateTime startedAt, LocalDateTime finishedAt) {
        private String key() {
            return startedAt.toString();
        }

        private String marker() {
            return "catchUpSlot=" + key();
        }
    }
}

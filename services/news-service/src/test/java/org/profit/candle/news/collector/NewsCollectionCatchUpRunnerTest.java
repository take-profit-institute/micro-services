package org.profit.candle.news.collector;

import org.junit.jupiter.api.Test;
import org.profit.candle.news.log.repository.CollectionLogJpaRepository;
import org.profit.candle.news.target.service.CollectionTargetSyncService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsCollectionCatchUpRunnerTest {
    @Test
    void shouldCollectOnlyNineSlotAtTenOClock() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        CollectionLogJpaRepository logRepository = mock(CollectionLogJpaRepository.class);
        NewsCollectionCatchUpRunner runner = runner(
                "2026-07-08T10:00:00+09:00",
                service,
                syncService,
                lock,
                logRepository
        );
        when(lock.tryLock()).thenReturn(Optional.of(new NewsCollectionLock.Handle(null)));
        when(logRepository.existsByMessageContaining(any())).thenReturn(false);
        when(logRepository.existsRegularCollectionBetween(any(), any())).thenReturn(false);
        when(service.collectActiveTargets(any())).thenReturn(new NewsCollectionResult(10, 10, 0));

        runner.runCatchUp();

        verify(syncService).syncListedStocksAsAdminTargets();
        verify(service).collectActiveTargets("catchUpSlot=2026-07-08T09:00");
        verify(service, times(1)).collectActiveTargets(any());
    }

    @Test
    void shouldCollectNineAndTwelveSlotsAtThirteenOClock() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        CollectionLogJpaRepository logRepository = mock(CollectionLogJpaRepository.class);
        NewsCollectionCatchUpRunner runner = runner(
                "2026-07-08T13:00:00+09:00",
                service,
                syncService,
                lock,
                logRepository
        );
        when(lock.tryLock()).thenReturn(Optional.of(new NewsCollectionLock.Handle(null)));
        when(logRepository.existsByMessageContaining(any())).thenReturn(false);
        when(logRepository.existsRegularCollectionBetween(any(), any())).thenReturn(false);
        when(service.collectActiveTargets(any())).thenReturn(new NewsCollectionResult(10, 10, 0));

        runner.runCatchUp();

        verify(service).collectActiveTargets("catchUpSlot=2026-07-08T09:00");
        verify(service).collectActiveTargets("catchUpSlot=2026-07-08T12:00");
        verify(service, times(2)).collectActiveTargets(any());
    }

    @Test
    void shouldCollectAllSlotsAfterSixteenOClock() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        CollectionLogJpaRepository logRepository = mock(CollectionLogJpaRepository.class);
        NewsCollectionCatchUpRunner runner = runner(
                "2026-07-08T16:00:00+09:00",
                service,
                syncService,
                lock,
                logRepository
        );
        when(lock.tryLock()).thenReturn(Optional.of(new NewsCollectionLock.Handle(null)));
        when(logRepository.existsByMessageContaining(any())).thenReturn(false);
        when(logRepository.existsRegularCollectionBetween(any(), any())).thenReturn(false);
        when(service.collectActiveTargets(any())).thenReturn(new NewsCollectionResult(10, 10, 0));

        runner.runCatchUp();

        verify(service).collectActiveTargets("catchUpSlot=2026-07-08T09:00");
        verify(service).collectActiveTargets("catchUpSlot=2026-07-08T12:00");
        verify(service).collectActiveTargets("catchUpSlot=2026-07-08T15:00");
        verify(service, times(3)).collectActiveTargets(any());
    }

    @Test
    void shouldSkipCompletedSlot() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        CollectionLogJpaRepository logRepository = mock(CollectionLogJpaRepository.class);
        NewsCollectionCatchUpRunner runner = runner(
                "2026-07-08T13:00:00+09:00",
                service,
                syncService,
                lock,
                logRepository
        );
        when(lock.tryLock()).thenReturn(Optional.of(new NewsCollectionLock.Handle(null)));
        when(logRepository.existsByMessageContaining("catchUpSlot=2026-07-08T09:00")).thenReturn(true);
        when(logRepository.existsByMessageContaining("catchUpSlot=2026-07-08T12:00")).thenReturn(false);
        when(logRepository.existsRegularCollectionBetween(any(), any())).thenReturn(false);
        when(service.collectActiveTargets(any())).thenReturn(new NewsCollectionResult(10, 10, 0));

        runner.runCatchUp();

        verify(service, never()).collectActiveTargets("catchUpSlot=2026-07-08T09:00");
        verify(service).collectActiveTargets("catchUpSlot=2026-07-08T12:00");
    }

    @Test
    void shouldSkipWhenLockIsNotAcquired() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        CollectionLogJpaRepository logRepository = mock(CollectionLogJpaRepository.class);
        NewsCollectionCatchUpRunner runner = runner(
                "2026-07-08T10:00:00+09:00",
                service,
                syncService,
                lock,
                logRepository
        );
        when(lock.tryLock()).thenReturn(Optional.empty());
        when(logRepository.existsByMessageContaining(any())).thenReturn(false);
        when(logRepository.existsRegularCollectionBetween(any(), any())).thenReturn(false);

        runner.runCatchUp();

        verify(syncService, never()).syncListedStocksAsAdminTargets();
        verify(service, never()).collectActiveTargets(any());
    }

    @Test
    void shouldSkipOnWeekend() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        CollectionLogJpaRepository logRepository = mock(CollectionLogJpaRepository.class);
        NewsCollectionCatchUpRunner runner = runner(
                "2026-07-11T16:00:00+09:00",
                service,
                syncService,
                lock,
                logRepository
        );

        runner.runCatchUp();

        verify(lock, never()).tryLock();
        verify(service, never()).collectActiveTargets(any());
    }

    @Test
    void shouldContinueWhenListedStockSyncFails() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        CollectionLogJpaRepository logRepository = mock(CollectionLogJpaRepository.class);
        NewsCollectionCatchUpRunner runner = runner(
                "2026-07-08T10:00:00+09:00",
                service,
                syncService,
                lock,
                logRepository
        );
        when(lock.tryLock()).thenReturn(Optional.of(new NewsCollectionLock.Handle(null)));
        when(logRepository.existsByMessageContaining(any())).thenReturn(false);
        when(logRepository.existsRegularCollectionBetween(any(), any())).thenReturn(false);
        doThrow(new IllegalStateException("stock unavailable"))
                .when(syncService)
                .syncListedStocksAsAdminTargets();
        when(service.collectActiveTargets(any())).thenReturn(new NewsCollectionResult(10, 10, 0));

        runner.runCatchUp();

        verify(service).collectActiveTargets(contains("catchUpSlot="));
    }

    private static NewsCollectionCatchUpRunner runner(
            String now,
            NewsCollectionService service,
            CollectionTargetSyncService syncService,
            NewsCollectionLock lock,
            CollectionLogJpaRepository logRepository
    ) {
        Clock clock = Clock.fixed(Instant.parse(now), ZoneId.of("Asia/Seoul"));
        return new NewsCollectionCatchUpRunner(service, syncService, lock, logRepository, clock);
    }
}

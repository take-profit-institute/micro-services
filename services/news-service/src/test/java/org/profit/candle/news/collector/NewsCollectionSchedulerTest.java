package org.profit.candle.news.collector;

import org.junit.jupiter.api.Test;
import org.profit.candle.news.target.service.CollectionTargetSyncService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsCollectionSchedulerTest {
    @Test
    void shouldNotPropagateCollectionFailure() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        when(lock.tryLock()).thenReturn(Optional.of(new NewsCollectionLock.Handle(null)));
        doThrow(new IllegalStateException("database unavailable"))
                .when(service)
                .collectActiveTargets();
        NewsCollectionScheduler scheduler = new NewsCollectionScheduler(service, syncService, lock);

        assertThatCode(scheduler::collectNews).doesNotThrowAnyException();
    }

    @Test
    void shouldSkipCollectionWhenLockIsNotAcquired() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        when(lock.tryLock()).thenReturn(Optional.empty());
        NewsCollectionScheduler scheduler = new NewsCollectionScheduler(service, syncService, lock);

        scheduler.collectNews();

        verify(syncService, never()).syncListedStocksAsAdminTargets();
        verify(service, never()).collectActiveTargets();
    }

    @Test
    void shouldNotPropagateLockFailure() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        when(lock.tryLock()).thenThrow(new IllegalStateException("lock failed"));
        NewsCollectionScheduler scheduler = new NewsCollectionScheduler(service, syncService, lock);

        assertThatCode(scheduler::collectNews).doesNotThrowAnyException();
        verify(syncService, never()).syncListedStocksAsAdminTargets();
        verify(service, never()).collectActiveTargets();
    }

    @Test
    void shouldContinueCollectionWhenListedStockSyncFails() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        CollectionTargetSyncService syncService = mock(CollectionTargetSyncService.class);
        NewsCollectionLock lock = mock(NewsCollectionLock.class);
        when(lock.tryLock()).thenReturn(Optional.of(new NewsCollectionLock.Handle(null)));
        doThrow(new IllegalStateException("stock unavailable"))
                .when(syncService)
                .syncListedStocksAsAdminTargets();
        NewsCollectionScheduler scheduler = new NewsCollectionScheduler(service, syncService, lock);

        assertThatCode(scheduler::collectNews).doesNotThrowAnyException();
        verify(service).collectActiveTargets();
    }
}

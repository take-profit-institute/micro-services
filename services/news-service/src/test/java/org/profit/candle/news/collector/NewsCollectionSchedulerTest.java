package org.profit.candle.news.collector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class NewsCollectionSchedulerTest {
    @Test
    void shouldNotPropagateCollectionFailure() {
        NewsCollectionService service = mock(NewsCollectionService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(service)
                .collectActiveTargets();
        NewsCollectionScheduler scheduler = new NewsCollectionScheduler(service);

        assertThatCode(scheduler::collectNews).doesNotThrowAnyException();
    }
}

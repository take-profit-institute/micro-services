package org.profit.candle.news.collector;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class NewsCollectionSleeper {
    void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("News collection interrupted", e);
        }
    }
}

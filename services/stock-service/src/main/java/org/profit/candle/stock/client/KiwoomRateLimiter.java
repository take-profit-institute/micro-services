package org.profit.candle.stock.client;

import java.util.concurrent.TimeUnit;
import org.profit.candle.stock.config.KiwoomProperties;
import org.springframework.stereotype.Component;

/**
 * 키움 REST 호출을 파드 단위로 초당 {@code ratePerSecond} 건으로 직렬화한다(최소 간격 방식).
 * 배치 concurrency가 높아 여러 스레드가 동시에 키움을 때려도 여기서 간격을 벌려 429를 줄인다.
 * (파드가 여러 개면 계정 한도를 공유하므로 파드 수만큼 나눈 값으로 설정해야 한다.)
 */
@Component
public class KiwoomRateLimiter {

    private final long intervalNanos;
    private long nextAvailableNanos;

    public KiwoomRateLimiter(KiwoomProperties properties) {
        double rate = properties.ratePerSecond() <= 0 ? 5 : properties.ratePerSecond();
        this.intervalNanos = (long) (TimeUnit.SECONDS.toNanos(1) / rate);
        this.nextAvailableNanos = System.nanoTime();
    }

    /** 다음 호출 슬롯이 열릴 때까지 블로킹한다. */
    public void acquire() {
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextAvailableNanos);
            waitNanos = slot - now;
            nextAvailableNanos = slot + intervalNanos;
        }
        if (waitNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("키움 rate limit 대기 중 인터럽트", e);
            }
        }
    }
}

package org.profit.candle.stock.chart.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

@Service
@Primary
@RequiredArgsConstructor
public class SingleFlightCandleBackfillService implements CandleBackfillService {

    private final DefaultCandleBackfillService delegate;
    private final ConcurrentMap<String, CompletableFuture<Integer>> inFlight = new ConcurrentHashMap<>();

    @Override
    public int backfill(String code, CandleInterval interval, int count) {
        String key = code + ":" + interval.storageValue();
        CompletableFuture<Integer> created = new CompletableFuture<>();
        CompletableFuture<Integer> current = inFlight.putIfAbsent(key, created);
        if (current != null) {
            return join(current);
        }

        try {
            int result = delegate.backfill(code, interval, count);
            created.complete(result);
            return result;
        } catch (RuntimeException e) {
            created.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, created);
        }
    }

    private static int join(CompletableFuture<Integer> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("캔들 백필 대기 중 인터럽트되었습니다", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("캔들 백필에 실패했습니다", cause);
        }
    }
}

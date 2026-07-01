package org.profit.candle.stock.chart.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.dto.CandleResult;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.exception.ChartErrorCode;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultChartService implements ChartService {

    private static final int MIN_LIMIT = 1;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final CandleReader candleReader;
    private final CandleBackfillService backfillService;

    @Override
    // 트랜잭션을 걸지 않는다 — DB miss fallback(HTTP) 동안 커넥션을 잡지 않기 위함.
    public List<CandleResult> getCandles(String code, CandleInterval interval, int requestedLimit, Instant to) {
        if (code == null || code.isBlank() || interval == null) {
            throw new CandleException(ChartErrorCode.INVALID_CANDLE_REQUEST);
        }
        int limit = normalizeLimit(requestedLimit);
        List<CandleEntity> candles = readLatest(code, interval, limit, to);

        if (candles.size() < limit) {
            backfillService.backfill(code, interval, limit, to);
            candles = readLatest(code, interval, limit, to);
        }

        if (candles.isEmpty()) {
            throw new CandleException(ChartErrorCode.CHART_DATA_UNAVAILABLE);
        }
        return candles.stream()
                .sorted(Comparator.comparing(c -> c.id().openTime()))
                .map(CandleResult::from)
                .toList();
    }

    protected List<CandleEntity> readLatest(String code, CandleInterval interval, int limit, Instant to) {
        return candleReader.findLatest(code, interval.storageValue(), to, limit);
    }

    private static int normalizeLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        if (requestedLimit < MIN_LIMIT) {
            return MIN_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }
}

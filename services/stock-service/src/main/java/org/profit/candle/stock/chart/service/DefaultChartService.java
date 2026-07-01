package org.profit.candle.stock.chart.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.dto.CandleResult;
import org.profit.candle.stock.chart.dto.SparklineResult;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.exception.ChartErrorCode;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.profit.candle.stock.chart.repository.SparklinePoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultChartService implements ChartService {

    private static final int MIN_LIMIT = 1;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_POINTS = 10;
    private static final int MAX_POINTS = 60;
    private static final int MAX_CODES = 100;

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

    @Override
    @Transactional(readOnly = true)
    public List<SparklineResult> getSparklines(List<String> codes, CandleInterval interval, int points) {
        if (codes == null || codes.isEmpty() || interval == null) {
            throw new CandleException(ChartErrorCode.INVALID_CANDLE_REQUEST);
        }
        List<String> distinct = codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .limit(MAX_CODES)
                .toList();
        if (distinct.isEmpty()) {
            throw new CandleException(ChartErrorCode.INVALID_CANDLE_REQUEST);
        }
        int normalized = normalizePoints(points);
        List<SparklinePoint> rows = candleReader.findRecentCloses(distinct, interval.storageValue(), normalized);

        // 쿼리는 (stock_code, open_time ASC) 정렬이라 종목별로 오래된 -> 최신 순으로 누적된다.
        Map<String, List<SparklinePoint>> byCode = new LinkedHashMap<>();
        for (SparklinePoint row : rows) {
            byCode.computeIfAbsent(row.getStockCode(), k -> new ArrayList<>()).add(row);
        }
        List<SparklineResult> results = new ArrayList<>();
        for (String code : distinct) {
            List<SparklinePoint> series = byCode.get(code);
            if (series == null || series.isEmpty()) {
                continue; // DB 에 데이터가 없는 종목은 결과에서 생략한다.
            }
            List<Long> closes = series.stream().map(SparklinePoint::getClose).toList();
            Instant last = series.get(series.size() - 1).getOpenTime();
            results.add(new SparklineResult(code, closes, last));
        }
        return results;
    }

    @Override
    // getCandles 와 마찬가지로 트랜잭션을 걸지 않는다 — DB miss 시 키움 백필(HTTP)을 탄다.
    public CandleResult getPreviousClose(String code, Instant date) {
        if (code == null || code.isBlank() || date == null) {
            throw new CandleException(ChartErrorCode.INVALID_CANDLE_REQUEST);
        }
        // findLatest(to=date) 는 open_time < date 인 가장 최근 일봉을 돌려준다(= 전 거래일).
        List<CandleEntity> found = candleReader.findLatest(code, CandleInterval.DAY_1.storageValue(), date, 1);
        if (found.isEmpty()) {
            backfillService.backfill(code, CandleInterval.DAY_1, DEFAULT_LIMIT, date);
            found = candleReader.findLatest(code, CandleInterval.DAY_1.storageValue(), date, 1);
        }
        if (found.isEmpty()) {
            throw new CandleException(ChartErrorCode.CHART_DATA_UNAVAILABLE);
        }
        return CandleResult.from(found.get(0));
    }

    protected List<CandleEntity> readLatest(String code, CandleInterval interval, int limit, Instant to) {
        return candleReader.findLatest(code, interval.storageValue(), to, limit);
    }

    private static int normalizePoints(int requested) {
        if (requested <= 0) {
            return DEFAULT_POINTS;
        }
        return Math.min(requested, MAX_POINTS);
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

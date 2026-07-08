package org.profit.candle.stock.chart.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.dto.CandleResult;
import org.profit.candle.stock.chart.dto.PriceStatsResult;
import org.profit.candle.stock.chart.dto.SparklineResult;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.exception.ChartErrorCode;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.profit.candle.stock.chart.repository.PriceStatsRow;
import org.profit.candle.stock.chart.repository.SparklinePoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
    private static final int DEFAULT_WINDOW_DAYS = 365;   // 약 52주
    private static final int MAX_WINDOW_DAYS = 366 * 5;   // 상한 5년

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
        List<String> distinct = normalizeCodes(codes);
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
    @Transactional(readOnly = true)
    public List<String> findExistingCandleCodes(List<String> codes, CandleInterval interval, Instant openTime) {
        if (codes == null || codes.isEmpty() || interval == null || openTime == null) {
            throw new CandleException(ChartErrorCode.INVALID_CANDLE_REQUEST);
        }
        List<String> distinct = normalizeCodes(codes);
        if (distinct.isEmpty()) {
            throw new CandleException(ChartErrorCode.INVALID_CANDLE_REQUEST);
        }
        return candleReader.findExistingCodesAt(distinct, interval.storageValue(), openTime);
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

    @Override
    @Transactional(readOnly = true)
    public PriceStatsResult getPriceStats(String code, int windowDays) {
        if (code == null || code.isBlank()) {
            throw new CandleException(ChartErrorCode.INVALID_CANDLE_REQUEST);
        }
        String interval = CandleInterval.DAY_1.storageValue();

        // 최근 일봉(종가/거래량/시각) — 없으면 이 종목은 캔들 데이터 자체가 없으므로 empty.
        List<CandleEntity> latest = candleReader.findLatest(code, interval, null, 1);
        if (latest.isEmpty()) {
            return PriceStatsResult.empty(code);
        }
        CandleEntity last = latest.get(0);

        Instant from = Instant.now().minus(Duration.ofDays(normalizeWindow(windowDays)));
        PriceStatsRow stats = candleReader.findPriceStats(code, interval, from);
        // window 안에 봉이 없으면(집계 NULL) 최근 봉 자체로 폴백해 최소한의 값을 채운다.
        long high = stats != null && stats.getHigh() != null ? stats.getHigh() : last.high();
        long low = stats != null && stats.getLow() != null ? stats.getLow() : last.low();

        return new PriceStatsResult(code, high, low, last.close(), last.volume(), last.id().openTime());
    }

    protected List<CandleEntity> readLatest(String code, CandleInterval interval, int limit, Instant to) {
        return candleReader.findLatest(code, interval.storageValue(), to, limit);
    }

    private static int normalizeWindow(int requested) {
        if (requested <= 0) {
            return DEFAULT_WINDOW_DAYS;
        }
        return Math.min(requested, MAX_WINDOW_DAYS);
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

    private static List<String> normalizeCodes(List<String> codes) {
        return codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .limit(MAX_CODES)
                .toList();
    }
}

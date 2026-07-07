package org.profit.candle.market.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.client.KiwoomMarketClient;
import org.profit.candle.market.dto.IntradayTickResult;
import org.profit.candle.market.dto.response.KiwoomTickChartResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 당일 실시간 그래프의 초기 스냅샷 제공.
 *
 * 키움 ka10079(주식틱차트)를 조회해 오늘치 틱을 {@link IntradayTickResult} 로 변환한다.
 * 장마감이어도 REST 로 당겨오므로 그래프를 그릴 수 있다.
 * 동일 종목 동시 조회 폭주 시 키움 rate-limit 를 피하려고 종목별 짧은 TTL 캐시를 둔다.
 */
@Service
@RequiredArgsConstructor
public class MarketIntradayService {

    private static final int DEFAULT_LIMIT = 600;
    private static final int MAX_LIMIT = 2000;
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final KiwoomMarketClient kiwoomMarketClient;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(List<IntradayTickResult> ticks, Instant at) {
    }

    /** 오래된 -> 최신 정렬로 최근 {@code limit} 개(0 이면 기본값)를 돌려준다. */
    public List<IntradayTickResult> getIntradayTicks(String symbol, int limit) {
        int n = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        Cached cached = cache.get(symbol);
        if (cached != null && Duration.between(cached.at(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return trim(cached.ticks(), n);
        }

        List<IntradayTickResult> all;
        try {
            KiwoomTickChartResponse response = kiwoomMarketClient.getTickChart(symbol);
            all = response.ticks() == null ? List.of()
                    : response.ticks().stream()
                            .map(this::toResult)
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparing(IntradayTickResult::time)) // 오래된 -> 최신
                            .toList();
        } catch (RuntimeException exception) {
            if (cached != null && !cached.ticks().isEmpty()) {
                return trim(cached.ticks(), n);
            }
            throw exception;
        }

        if (all.isEmpty() && cached != null && !cached.ticks().isEmpty()) {
            return trim(cached.ticks(), n);
        }
        if (all.isEmpty()) {
            return List.of();
        }
        cache.put(symbol, new Cached(all, Instant.now()));
        return trim(all, n);
    }

    private static List<IntradayTickResult> trim(List<IntradayTickResult> all, int n) {
        return all.size() <= n ? all : all.subList(all.size() - n, all.size());
    }

    private IntradayTickResult toResult(KiwoomTickChartResponse.Item item) {
        try {
            long price = Long.parseLong(item.currentPrice().replaceAll("[+,\\-]", "").trim());
            Instant time = LocalDateTime.parse(item.contractTime().trim(), TIME_FMT).atZone(KST).toInstant();
            return new IntradayTickResult(price, time);
        } catch (RuntimeException e) {
            return null; // 파싱 불가 틱은 스킵
        }
    }
}

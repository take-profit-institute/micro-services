package org.profit.candle.stock.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.config.KiwoomProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RestKiwoomChartClient implements KiwoomChartClient {

    private static final String DAILY_CHART_TR = "ka10081";
    private static final String WEEKLY_CHART_TR = "ka10082";
    private static final String MONTHLY_CHART_TR = "ka10083";
    private static final int MAX_PAGES = 100;

    private final KiwoomProperties properties;
    private final RestClient kiwoomRestClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    @Override
    public List<KiwoomCandleData> fetchCandles(String code, CandleInterval interval, int count) {
        if (!properties.enabled()) {
            return List.of();
        }
        try {
            return fetchPages(code, interval, count);
        } catch (RuntimeException e) {
            log.warn("키움 차트 조회 실패 code={} interval={}: {}", code, interval, e.toString());
            return List.of();
        }
    }

    private List<KiwoomCandleData> fetchPages(String code, CandleInterval interval, int count) {
        String token = accessToken();
        List<KiwoomCandleData> result = new ArrayList<>();
        String contYn = "N";
        String nextKey = "";
        int pages = 0;

        do {
            ResponseEntity<Map<String, Object>> response = kiwoomRestClient.post()
                    .uri(properties.chartPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("authorization", "Bearer " + token)
                    .header("api-id", apiId(interval))
                    .header("cont-yn", contYn)
                    .header("next-key", nextKey)
                    .body(Map.of("stk_cd", code))
                    .retrieve()
                    .toEntity(MAP_TYPE);

            extractRows(response.getBody(), code, interval, result);

            HttpHeaders headers = response.getHeaders();
            contYn = header(headers, "cont-yn");
            nextKey = header(headers, "next-key");
        } while (result.size() < count
                && "Y".equalsIgnoreCase(contYn)
                && !nextKey.isBlank()
                && ++pages < MAX_PAGES);

        return result.stream()
                .sorted(Comparator.comparing(KiwoomCandleData::openTime))
                .skip(Math.max(0, result.size() - count))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private void extractRows(Map<String, Object> body, String code, CandleInterval interval, List<KiwoomCandleData> out) {
        if (body == null) {
            return;
        }
        List<Object> rows = null;
        for (String key : List.of("output", "list", "chart", "candles")) {
            Object named = body.get(key);
            if (named instanceof List<?> list) {
                rows = (List<Object>) list;
                break;
            }
        }
        if (rows == null) {
            for (Object value : body.values()) {
                if (value instanceof List<?> list) {
                    rows = (List<Object>) list;
                    break;
                }
            }
        }
        if (rows == null) {
            return;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> r = (Map<String, Object>) raw;
            Instant openTime = openTime(r);
            Long open = firstLong(r, "open", "open_pric", "stck_oprc");
            Long high = firstLong(r, "high", "high_pric", "stck_hgpr");
            Long low = firstLong(r, "low", "low_pric", "stck_lwpr");
            Long close = firstLong(r, "close", "cur_prc", "clos_pric", "stck_clpr");
            Long volume = firstLong(r, "volume", "trde_qty", "acml_vol");
            if (openTime == null || open == null || high == null || low == null || close == null || volume == null) {
                continue;
            }
            out.add(new KiwoomCandleData(code, interval, openTime,
                    Math.abs(open), Math.abs(high), Math.abs(low), Math.abs(close), Math.abs(volume)));
        }
    }

    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        Map<String, Object> response = kiwoomRestClient.post()
                .uri(properties.tokenPath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", properties.appKey(),
                        "secretkey", properties.appSecret()))
                .retrieve()
                .body(MAP_TYPE);

        String token = response == null ? null : firstNonNull(str(response, "token"), str(response, "access_token"));
        if (token == null) {
            throw new IllegalStateException("키움 토큰 응답에 token 없음");
        }
        Long ttl = lng(response, "expires_in");
        long seconds = ttl != null ? ttl : 3600L;
        this.cachedToken = token;
        this.tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, seconds - 60));
        return token;
    }

    private static String apiId(CandleInterval interval) {
        return switch (interval) {
            case DAY_1 -> DAILY_CHART_TR;
            case WEEK_1 -> WEEKLY_CHART_TR;
            case MONTH_1 -> MONTHLY_CHART_TR;
        };
    }

    private static Instant openTime(Map<String, Object> row) {
        String raw = firstNonNull(str(row, "date"), str(row, "dt"));
        raw = firstNonNull(raw, str(row, "stck_bsop_date"));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replaceAll("[^0-9]", "");
        if (normalized.length() < 8) {
            return null;
        }
        LocalDate date = LocalDate.parse(normalized.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Long firstLong(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Long value = lng(row, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String header(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        return value == null ? "" : value.trim();
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new org.springframework.core.ParameterizedTypeReference<>() {
            };

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Long lng(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        try {
            String s = String.valueOf(value).replaceAll("[,+\\s]", "");
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }
}

package org.profit.candle.stock.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.stock.config.KiwoomProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 키움 OpenAPI REST 구현.
 *
 * 미설정(키 없음) 시 모든 호출이 빈 결과를 반환하므로 앱은 키 없이도 기동/동작한다.
 * 응답 필드 키(stk_cd/stk_nm 등)와 api-id(TR)는 키움 REST 스펙에 맞춰 조정이 필요할 수 있어
 * 파싱은 방어적으로 처리한다(실패 시 empty). 실제 계정 응답으로 검증 후 확정할 것.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RestKiwoomStockClient implements KiwoomStockClient {

    // 종목정보 조회 TR (키움 REST). 계정 스펙에 맞게 조정 가능.
    private static final String STOCK_INFO_TR = "ka10100";
    // 종목정보 리스트 TR (시장별 전체 종목). 스펙 확인 후 조정.
    private static final String STOCK_LIST_TR = "ka10099";
    // 연속조회 무한루프 방지 상한.
    private static final int MAX_PAGES = 200;

    private final KiwoomProperties properties;
    private final RestClient kiwoomRestClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    @Override
    public Optional<KiwoomStockData> findStock(String code) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        try {
            String token = accessToken();
            Map<String, Object> body = kiwoomRestClient.post()
                    .uri(properties.stockInfoPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("authorization", "Bearer " + token)
                    .header("api-id", STOCK_INFO_TR)
                    .body(Map.of("stk_cd", code))
                    .retrieve()
                    .body(MAP_TYPE);
            return toStockData(code, body);
        } catch (RuntimeException e) {
            log.warn("키움 findStock 실패 code={}: {}", code, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public List<KiwoomStockData> findAllStocksByMarket(String marketType) {
        if (!properties.enabled()) {
            return List.of();
        }
        if (marketType == null || marketType.isBlank()) {
            List<KiwoomStockData> all = new ArrayList<>();
            all.addAll(fetchMarket("KOSPI"));
            all.addAll(fetchMarket("KOSDAQ"));
            return all;
        }
        return fetchMarket(marketType);
    }

    /**
     * 시장별 전체 종목을 키움 연속조회로 모두 가져온다.
     * 키움 REST 페이징: 응답 헤더 cont-yn=Y + next-key 를 다음 요청 헤더로 되던져 cont-yn=N 까지 반복한다.
     */
    private List<KiwoomStockData> fetchMarket(String marketType) {
        String mrktTp = toKiwoomMarketCode(marketType);
        String token = accessToken();
        List<KiwoomStockData> result = new ArrayList<>();

        String contYn = "N";   // 최초 호출
        String nextKey = "";
        int pages = 0;

        do {
            ResponseEntity<Map<String, Object>> response = kiwoomRestClient.post()
                    .uri(properties.stockListPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("authorization", "Bearer " + token)
                    .header("api-id", STOCK_LIST_TR)
                    .header("cont-yn", contYn)
                    .header("next-key", nextKey)
                    .body(Map.of("mrkt_tp", mrktTp))
                    .retrieve()
                    .toEntity(MAP_TYPE);

            extractRows(response.getBody(), marketType, result);

            HttpHeaders headers = response.getHeaders();
            contYn = header(headers, "cont-yn");
            nextKey = header(headers, "next-key");
        } while ("Y".equalsIgnoreCase(contYn) && !nextKey.isBlank() && ++pages < MAX_PAGES);

        log.info("키움 종목목록 조회 완료 market={} count={} pages={}", marketType, result.size(), pages + 1);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void extractRows(Map<String, Object> body, String marketType, List<KiwoomStockData> out) {
        if (body == null) {
            return;
        }
        // 응답의 목록 배열: TR 스펙에 따라 키가 다를 수 있어(list/stk_list/output 등) 첫 List 값을 사용한다.
        List<Object> rows = null;
        Object named = body.get("list");
        if (named instanceof List<?> l) {
            rows = (List<Object>) l;
        } else {
            for (Object v : body.values()) {
                if (v instanceof List<?> l) {
                    rows = (List<Object>) l;
                    break;
                }
            }
        }
        if (rows == null) {
            return;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> r = (Map<String, Object>) m;
            String code = firstNonNull(str(r, "code"), str(r, "stk_cd"));
            String name = firstNonNull(str(r, "name"), str(r, "stk_nm"));
            if (code == null || code.isBlank() || name == null || name.isBlank()) {
                continue;
            }
            out.add(new KiwoomStockData(
                    code,
                    name,
                    marketType,
                    str(r, "sector"),
                    lng(r, "mac"),        // 시가총액
                    lng(r, "lst_stk"),    // 상장주식수
                    null,                 // 상장일: 스펙 확인 후 파싱
                    "LISTED"));
        }
    }

    /** KOSPI/KOSDAQ → 키움 시장구분 코드. 스펙 확인 후 조정. */
    private static String toKiwoomMarketCode(String marketType) {
        return "KOSDAQ".equalsIgnoreCase(marketType) ? "10" : "0";
    }

    private static String header(HttpHeaders headers, String name) {
        String v = headers.getFirst(name);
        return v == null ? "" : v.trim();
    }

    private Optional<KiwoomStockData> toStockData(String code, Map<String, Object> body) {
        if (body == null) {
            return Optional.empty();
        }
        String name = str(body, "stk_nm");
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new KiwoomStockData(
                code,
                name,
                normalizeMarket(str(body, "mrkt_tp")),
                str(body, "sector"),
                lng(body, "mac"),          // 시가총액
                lng(body, "lst_stk"),      // 상장주식수
                null,                      // 상장일: 스펙 확인 후 파싱
                "LISTED"));
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
        this.tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, seconds - 60)); // 만료 60초 전 갱신
        return token;
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new org.springframework.core.ParameterizedTypeReference<>() {
            };

    private static String normalizeMarket(String raw) {
        if (raw == null) {
            return "KOSPI";
        }
        return raw.toUpperCase().contains("KOSDAQ") ? "KOSDAQ" : "KOSPI";
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    private static Long lng(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        try {
            String s = String.valueOf(v).replaceAll("[,\\s]", "");
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}

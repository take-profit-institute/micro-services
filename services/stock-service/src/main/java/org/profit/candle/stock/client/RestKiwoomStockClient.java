package org.profit.candle.stock.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.stock.config.KiwoomProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
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
        // 벌크 종목목록 TR(예: ka10099)은 배치 도입 시 연결한다. 현재는 미구현.
        log.info("키움 findAllStocksByMarket 미구현 — 배치 도입 시 연결 예정 (market={})", marketType);
        return List.of();
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

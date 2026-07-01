package org.profit.candle.stock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 키움 OpenAPI 설정. app-key/app-secret 이 비어 있으면 {@link #enabled()} == false 이고
 * fallback 은 동작하지 않는다(상세 조회 시 DB에 없으면 NOT_FOUND).
 *
 * token-path / stock-info-path 는 키움 REST 스펙에 맞춰 외부에서 조정 가능하도록 분리했다.
 */
@ConfigurationProperties(prefix = "kiwoom")
public record KiwoomProperties(
        String baseUrl,
        String appKey,
        String appSecret,
        Duration staleness,
        String tokenPath,
        String stockInfoPath,
        String stockListPath) {

    public KiwoomProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.kiwoom.com";
        if (staleness == null) staleness = Duration.ofDays(7);
        if (tokenPath == null || tokenPath.isBlank()) tokenPath = "/oauth2/token";
        if (stockInfoPath == null || stockInfoPath.isBlank()) stockInfoPath = "/api/dostk/stkinfo";
        if (stockListPath == null || stockListPath.isBlank()) stockListPath = "/api/dostk/stkinfo";
    }

    public boolean enabled() {
        return appKey != null && !appKey.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}

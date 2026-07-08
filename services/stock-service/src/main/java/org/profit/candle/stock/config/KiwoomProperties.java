package org.profit.candle.stock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 키움 OpenAPI 설정. app-key/app-secret 이 비어 있으면 {@link #enabled()} == false 이고
 * fallback 은 동작하지 않는다(상세 조회 시 DB에 없으면 NOT_FOUND).
 *
 * token-path / stock-info-path / chart-path 는 키움 REST 스펙에 맞춰 외부에서 조정 가능하도록 분리했다.
 */
@ConfigurationProperties(prefix = "kiwoom")
public record KiwoomProperties(
        String baseUrl,
        String appKey,
        String appSecret,
        Duration staleness,
        String tokenPath,
        String stockInfoPath,
        String stockListPath,
        String chartPath,
        Duration connectTimeout,
        Duration readTimeout,
        double ratePerSecond,
        int rateLimitMaxAttempts,
        Duration rateLimitBackoff) {

    public KiwoomProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.kiwoom.com";
        if (staleness == null) staleness = Duration.ofDays(7);
        if (tokenPath == null || tokenPath.isBlank()) tokenPath = "/oauth2/token";
        if (stockInfoPath == null || stockInfoPath.isBlank()) stockInfoPath = "/api/dostk/stkinfo";
        if (stockListPath == null || stockListPath.isBlank()) stockListPath = "/api/dostk/stkinfo";
        if (chartPath == null || chartPath.isBlank()) chartPath = "/api/dostk/chart";
        // 차트 첫 페이지는 수백 개 캔들을 한 번에 내려줘 5s 로는 read timeout 이 발생한다(§11).
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(3);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(15);
        // 키움 차트 API(ka10081)는 계정당 초당 5건(유량=5)이 상한. 파드가 여러 개면 계정을 공유하므로
        // 파드 수만큼 나눠(예: 2파드면 2~3/s) 설정하고, 초과분은 429 백오프 재시도로 흡수한다.
        if (ratePerSecond <= 0) ratePerSecond = 5;
        if (rateLimitMaxAttempts <= 0) rateLimitMaxAttempts = 4;
        if (rateLimitBackoff == null) rateLimitBackoff = Duration.ofMillis(500);
    }

    public boolean enabled() {
        return appKey != null && !appKey.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}

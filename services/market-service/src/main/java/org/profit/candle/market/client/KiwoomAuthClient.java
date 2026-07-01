package org.profit.candle.market.client;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.dto.request.KiwoomTokenRequest;
import org.profit.candle.market.dto.response.KiwoomTokenResponse;
import org.profit.candle.market.exception.MarketErrorCode;
import org.profit.candle.market.exception.MarketException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class KiwoomAuthClient {

    private final WebClient kiwoomWebClient;

    @Value("${kiwoom.app-key}")
    private String appKey;

    @Value("${kiwoom.secret-key}")
    private String secretKey;

    @Value("${kiwoom.token-cache-ttl:PT50M}")
    private Duration tokenCacheTtl;

    private volatile KiwoomTokenResponse cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public synchronized KiwoomTokenResponse issueToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        KiwoomTokenRequest request = new KiwoomTokenRequest(
                "client_credentials",
                appKey,
                secretKey
        );

        KiwoomTokenResponse response = kiwoomWebClient.post()
                .uri("/oauth2/token")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("api-id", "au10001")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, httpResponse -> httpResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new MarketException(MarketErrorCode.KIWOOM_HTTP_FAILED))))
                .bodyToMono(KiwoomTokenResponse.class)
                .block();

        validate(response);
        cachedToken = response;
        tokenExpiresAt = Instant.now().plus(tokenCacheTtl == null ? Duration.ofMinutes(50) : tokenCacheTtl);
        return response;
    }

    private static void validate(KiwoomTokenResponse response) {
        if (response == null) {
            throw new MarketException(MarketErrorCode.KIWOOM_INVALID_RESPONSE);
        }
        if (response.returnCode() != 0) {
            throw new MarketException(MarketErrorCode.KIWOOM_BUSINESS_FAILED);
        }
        if (response.token() == null || response.token().isBlank()) {
            throw new MarketException(MarketErrorCode.KIWOOM_INVALID_RESPONSE);
        }
    }
}

package org.profit.candle.market.client;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.dto.request.KiwoomTokenRequest;
import org.profit.candle.market.dto.response.KiwoomTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KiwoomAuthClient {

    private final WebClient kiwoomWebClient;

    @Value("${kiwoom.app-key}")
    private String appKey;

    @Value("${kiwoom.secret-key}")
    private String secretKey;

    public KiwoomTokenResponse issueToken() {
        KiwoomTokenRequest request = new KiwoomTokenRequest(
                "client_credentials",
                appKey,
                secretKey
        );

        return kiwoomWebClient.post()
                .uri("/oauth2/token")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("api-id", "au10001")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(KiwoomTokenResponse.class)
                .block();
    }
}

package org.profit.candle.market.ranking.client;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.client.KiwoomAuthClient;
import org.profit.candle.market.ranking.dto.request.KiwoomPriceRankRequest;
import org.profit.candle.market.ranking.dto.response.KiwoomPriceRankResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KiwoomRankingClient {

    private final WebClient kiwoomWebClient;
    private final KiwoomAuthClient kiwoomAuthClient;

    public KiwoomPriceRankResponse getRisingStocks() {
        return requestPriceRank(KiwoomPriceRankRequest.rising());
    }

    public KiwoomPriceRankResponse getFallingStocks() {
        return requestPriceRank(KiwoomPriceRankRequest.falling());
    }

    public KiwoomPriceRankResponse requestPriceRank(KiwoomPriceRankRequest request) {
        String token = kiwoomAuthClient.issueToken().token();

        KiwoomPriceRankResponse response = kiwoomWebClient.post()
                .uri("/api/dostk/stkinfo")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                .header("api-id", "ka10019")
                .header("authorization", "Bearer " + token)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(KiwoomPriceRankResponse.class)
                .block();

        return response;
    }

}

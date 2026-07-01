package org.profit.candle.market.client;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.dto.request.KiwoomStockRequest;
import org.profit.candle.market.dto.response.KiwoomStockResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class KiwoomMarketClient {

    private final WebClient kiwoomWebClient;
    private final KiwoomAuthClient kiwoomAuthClient;

    public KiwoomStockResponse getStockInfo(String stockCode){
        String token = kiwoomAuthClient.issueToken().token();

        KiwoomStockRequest request = new KiwoomStockRequest(stockCode);

        return kiwoomWebClient.post()
                .uri("/api/dostk/stkinfo")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                .header("api-id", "ka10001")
                .header("authorization", "Bearer " + token)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(KiwoomStockResponse.class)
                .block();
    }
}

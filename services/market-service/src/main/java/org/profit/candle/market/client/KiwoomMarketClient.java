package org.profit.candle.market.client;

import lombok.RequiredArgsConstructor;
import org.profit.candle.market.dto.request.KiwoomOrderBookRequest;
import org.profit.candle.market.dto.request.KiwoomStockRequest;
import org.profit.candle.market.dto.request.KiwoomTickChartRequest;
import org.profit.candle.market.dto.response.KiwoomOrderBookResponse;
import org.profit.candle.market.dto.response.KiwoomStockResponse;
import org.profit.candle.market.dto.response.KiwoomTickChartResponse;
import org.profit.candle.market.exception.MarketErrorCode;
import org.profit.candle.market.exception.MarketException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class KiwoomMarketClient {

    private final WebClient kiwoomWebClient;
    private final KiwoomAuthClient kiwoomAuthClient;

    public KiwoomStockResponse getStockInfo(String stockCode){
        String token = token();

        KiwoomStockRequest request = new KiwoomStockRequest(stockCode);

        KiwoomStockResponse response = kiwoomWebClient.post()
                .uri("/api/dostk/stkinfo")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                .header("api-id", "ka10001")
                .header("authorization", "Bearer " + token)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, httpResponse -> httpResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new MarketException(MarketErrorCode.KIWOOM_HTTP_FAILED))))
                .bodyToMono(KiwoomStockResponse.class)
                .block();
        validate(response);
        return response;
    }

    /** 주식틱차트조회(ka10079) — 당일 틱 시리즈. 초기 그래프 스냅샷용. */
    public KiwoomTickChartResponse getTickChart(String stockCode) {
        String token = token();

        KiwoomTickChartResponse response = kiwoomWebClient.post()
                .uri("/api/dostk/chart")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                .header("api-id", "ka10079")
                .header("authorization", "Bearer " + token)
                .bodyValue(KiwoomTickChartRequest.ofOneTick(stockCode))
                .retrieve()
                .onStatus(HttpStatusCode::isError, httpResponse -> httpResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new MarketException(MarketErrorCode.KIWOOM_HTTP_FAILED))))
                .bodyToMono(KiwoomTickChartResponse.class)
                .block();

        if (response == null) {
            throw new MarketException(MarketErrorCode.KIWOOM_INVALID_RESPONSE);
        }
        if (response.returnCode() != 0) {
            throw new MarketException(MarketErrorCode.KIWOOM_BUSINESS_FAILED);
        }
        return response;
    }

    /** 주식호가요청(ka10004) — 현재 매수/매도 호가 10단계. */
    public KiwoomOrderBookResponse getOrderBook(String stockCode) {
        String token = token();

        KiwoomOrderBookResponse response = kiwoomWebClient.post()
                .uri("/api/dostk/mrkcond")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                .header("api-id", "ka10004")
                .header("authorization", "Bearer " + token)
                .bodyValue(new KiwoomOrderBookRequest(stockCode))
                .retrieve()
                .onStatus(HttpStatusCode::isError, httpResponse -> httpResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new MarketException(MarketErrorCode.KIWOOM_HTTP_FAILED))))
                .bodyToMono(KiwoomOrderBookResponse.class)
                .block();

        if (response == null) {
            throw new MarketException(MarketErrorCode.KIWOOM_INVALID_RESPONSE);
        }
        if (response.returnCode() != null && response.returnCode() != 0) {
            throw new MarketException(MarketErrorCode.KIWOOM_BUSINESS_FAILED);
        }
        return response;
    }

    private String token() {
        var response = kiwoomAuthClient.issueToken();
        if (response == null || response.token() == null || response.token().isBlank()) {
            throw new MarketException(MarketErrorCode.KIWOOM_INVALID_RESPONSE);
        }
        return response.token();
    }

    private static void validate(KiwoomStockResponse response) {
        if (response == null || response.stockCode() == null || response.stockCode().isBlank()) {
            throw new MarketException(MarketErrorCode.KIWOOM_INVALID_RESPONSE);
        }
        if (response.returnCode() != 0) {
            throw new MarketException(MarketErrorCode.KIWOOM_BUSINESS_FAILED);
        }
    }
}

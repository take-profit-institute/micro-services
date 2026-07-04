package org.profit.candle.news.naver.client;

import lombok.RequiredArgsConstructor;
import org.profit.candle.news.naver.NaverNewsProperties;
import org.profit.candle.news.naver.dto.NaverNewsSearchRequest;
import org.profit.candle.news.naver.dto.NaverNewsSearchResponse;
import org.profit.candle.news.naver.dto.NaverNewsSearchResult;
import org.profit.candle.news.naver.exception.NaverNewsApiException;
import org.profit.candle.news.naver.mapper.NaverNewsMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.AUTHORIZATION_FAILED;
import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.EMPTY_RESPONSE;
import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.INVALID_REQUEST;
import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.REQUEST_FAILED;
import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.SERVER_ERROR;

@Component
@RequiredArgsConstructor
public class RestNaverNewsClient implements NaverNewsClient {
    private static final String SEARCH_NEWS_PATH = "/v1/search/news.json";
    private static final String CLIENT_ID_HEADER = "X-Naver-Client-Id";
    private static final String CLIENT_SECRET_HEADER = "X-Naver-Client-Secret";

    private final RestClient naverNewsRestClient;
    private final NaverNewsProperties properties;

    @Override
    public NaverNewsSearchResult search(NaverNewsSearchRequest request) {
        try {
            NaverNewsSearchResponse response = naverNewsRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(SEARCH_NEWS_PATH)
                            .queryParam("query", request.query())
                            .queryParam("display", request.display())
                            .queryParam("start", request.start())
                            .queryParam("sort", request.sort())
                            .build())
                    .header(CLIENT_ID_HEADER, properties.clientId())
                    .header(CLIENT_SECRET_HEADER, properties.clientSecret())
                    .retrieve()
                    .body(NaverNewsSearchResponse.class);

            if (response == null) {
                throw new NaverNewsApiException(EMPTY_RESPONSE);
            }
            return NaverNewsMapper.toResult(response);
        } catch (RestClientResponseException e) {
            throw new NaverNewsApiException(reason(e.getStatusCode().value()), e);
        } catch (RestClientException e) {
            throw new NaverNewsApiException(REQUEST_FAILED, e);
        }
    }

    private static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason reason(int statusCode) {
        if (statusCode == 400) {
            return INVALID_REQUEST;
        }
        if (statusCode == 401 || statusCode == 403) {
            return AUTHORIZATION_FAILED;
        }
        if (statusCode >= 500) {
            return SERVER_ERROR;
        }
        return REQUEST_FAILED;
    }
}

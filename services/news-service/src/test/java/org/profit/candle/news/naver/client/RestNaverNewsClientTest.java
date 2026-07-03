package org.profit.candle.news.naver.client;

import org.junit.jupiter.api.Test;
import org.profit.candle.news.naver.NaverNewsProperties;
import org.profit.candle.news.naver.dto.NaverNewsSearchRequest;
import org.profit.candle.news.naver.exception.NaverNewsApiException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.AUTHORIZATION_FAILED;
import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.INVALID_REQUEST;
import static org.profit.candle.news.naver.exception.NaverNewsApiFailureReason.SERVER_ERROR;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.GET;

class RestNaverNewsClientTest {
    @Test
    void shouldClassifyBadRequest() {
        assertFailureReason(HttpStatus.BAD_REQUEST, INVALID_REQUEST.message());
    }

    @Test
    void shouldClassifyAuthorizationFailure() {
        assertFailureReason(HttpStatus.UNAUTHORIZED, AUTHORIZATION_FAILED.message());
        assertFailureReason(HttpStatus.FORBIDDEN, AUTHORIZATION_FAILED.message());
    }

    @Test
    void shouldClassifyServerError() {
        assertFailureReason(HttpStatus.INTERNAL_SERVER_ERROR, SERVER_ERROR.message());
    }

    private static void assertFailureReason(HttpStatus status, String expectedMessage) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://openapi.naver.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestNaverNewsClient client = new RestNaverNewsClient(builder.build(), properties());

        server.expect(requestTo("https://openapi.naver.com/v1/search/news.json?query=Samsung&display=3&start=1&sort=date"))
                .andExpect(method(GET))
                .andExpect(header("X-Naver-Client-Id", "client-id"))
                .andExpect(header("X-Naver-Client-Secret", "client-secret"))
                .andRespond(withStatus(status).body("{\"error\":\"secret body\"}"));

        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("Samsung", 3, 1, "date")))
                .isInstanceOfSatisfying(NaverNewsApiException.class, exception ->
                        assertThat(exception.getMessage()).isEqualTo(expectedMessage))
                .hasMessageNotContaining("secret body");

        server.verify();
    }

    private static NaverNewsProperties properties() {
        return new NaverNewsProperties(
                "client-id",
                "client-secret",
                URI.create("https://openapi.naver.com"),
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)
        );
    }
}

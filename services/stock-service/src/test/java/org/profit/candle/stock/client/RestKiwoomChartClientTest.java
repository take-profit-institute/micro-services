package org.profit.candle.stock.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.exception.ChartErrorCode;
import org.profit.candle.stock.config.KiwoomProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestKiwoomChartClientTest {

    private static final String BASE = "https://api.kiwoom.com";
    private static final String TOKEN_JSON = "{\"token\":\"t\",\"expires_in\":3600}";
    private static final String CHART_JSON = "{\"output\":[{\"date\":\"20260708\",\"open\":\"100\","
            + "\"high\":\"110\",\"low\":\"90\",\"close\":\"105\",\"volume\":\"1000\"}]}";
    private static final String RATE_LIMIT_JSON =
            "{\"return_code\":5,\"return_msg\":\"허용된 요청 개수를 초과하였습니다\"}";

    private KiwoomProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RestKiwoomChartClient client;

    @BeforeEach
    void setUp() {
        // 테스트는 백오프를 1ms로, 스로틀은 사실상 off로 둬 빠르게 돈다.
        properties = new KiwoomProperties(BASE, "key", "secret", null, null, null, null, null,
                null, null, 1000, 3, Duration.ofMillis(1));
        builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestKiwoomChartClient(properties, builder.build(), new KiwoomRateLimiter(properties));
    }

    @Test
    void retriesOn429ThenSucceeds() {
        server.expect(once(), requestTo(BASE + "/oauth2/token"))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/api/dostk/chart"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body(RATE_LIMIT_JSON).contentType(MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/api/dostk/chart"))
                .andRespond(withSuccess(CHART_JSON, MediaType.APPLICATION_JSON));

        List<KiwoomCandleData> candles =
                client.fetchCandles("000660", CandleInterval.DAY_1, 1, null);

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().close()).isEqualTo(105);
        server.verify();
    }

    @Test
    void throwsRateLimitedWhenRetriesExhausted() {
        server.expect(once(), requestTo(BASE + "/oauth2/token"))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
        for (int i = 0; i < 3; i++) {
            server.expect(once(), requestTo(BASE + "/api/dostk/chart"))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .body(RATE_LIMIT_JSON).contentType(MediaType.APPLICATION_JSON));
        }

        assertThatThrownBy(() -> client.fetchCandles("000660", CandleInterval.DAY_1, 1, null))
                .isInstanceOf(CandleException.class)
                .extracting(e -> ((CandleException) e).errorCode())
                .isEqualTo(ChartErrorCode.KIWOOM_RATE_LIMITED);
        server.verify();
    }

    @Test
    void otherHttpErrorsFallBackToEmpty() {
        server.expect(once(), requestTo(BASE + "/oauth2/token"))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/api/dostk/chart"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        List<KiwoomCandleData> candles =
                client.fetchCandles("000660", CandleInterval.DAY_1, 1, null);

        assertThat(candles).isEmpty();
        server.verify();
    }
}

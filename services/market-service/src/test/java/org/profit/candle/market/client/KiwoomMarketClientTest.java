package org.profit.candle.market.client;

import org.junit.jupiter.api.Test;
import org.profit.candle.market.dto.response.KiwoomTokenResponse;
import org.profit.candle.market.exception.MarketErrorCode;
import org.profit.candle.market.exception.MarketException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KiwoomMarketClientTest {

    @Test
    void getStockInfo_rejectsMissingToken() {
        KiwoomAuthClient authClient = mock(KiwoomAuthClient.class);
        when(authClient.issueToken()).thenReturn(null);
        KiwoomMarketClient client = new KiwoomMarketClient(WebClient.builder().baseUrl("http://localhost").build(), authClient);

        assertThatThrownBy(() -> client.getStockInfo("005930"))
                .isInstanceOf(MarketException.class)
                .satisfies(e -> assertThat(((MarketException) e).errorCode())
                        .isEqualTo(MarketErrorCode.KIWOOM_INVALID_RESPONSE));
    }

    @Test
    void getStockInfo_rejectsBusinessFailure() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/api/dostk/stkinfo", (request, response) -> response
                        .header("Content-Type", "application/json")
                        .sendString(reactor.core.publisher.Mono.just("""
                                {"stk_cd":"005930","return_code":1,"return_msg":"failed"}
                                """))))
                .bindNow();
        try {
            KiwoomAuthClient authClient = mock(KiwoomAuthClient.class);
            when(authClient.issueToken()).thenReturn(new KiwoomTokenResponse("", "Bearer", "token", 0, "OK"));
            KiwoomMarketClient client = new KiwoomMarketClient(WebClient.builder()
                    .baseUrl("http://localhost:" + server.port())
                    .build(), authClient);

            assertThatThrownBy(() -> client.getStockInfo("005930"))
                    .isInstanceOf(MarketException.class)
                    .satisfies(e -> assertThat(((MarketException) e).errorCode())
                            .isEqualTo(MarketErrorCode.KIWOOM_BUSINESS_FAILED));
        } finally {
            server.disposeNow();
        }
    }
}

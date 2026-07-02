package org.profit.candle.market.client;

import org.junit.jupiter.api.Test;
import org.profit.candle.market.exception.MarketErrorCode;
import org.profit.candle.market.exception.MarketException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KiwoomAuthClientTest {

    @Test
    void issueToken_cachesTokenResponse() {
        AtomicInteger calls = new AtomicInteger();
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/oauth2/token", (request, response) -> {
                    calls.incrementAndGet();
                    return response.header("Content-Type", "application/json")
                            .sendString(reactor.core.publisher.Mono.just("""
                                    {"token":"token-1","token_type":"Bearer","expires_dt":"20991231235959","return_code":0,"return_msg":"OK"}
                                    """));
                }))
                .bindNow();
        try {
            KiwoomAuthClient client = client(server);

            assertThat(client.issueToken().token()).isEqualTo("token-1");
            assertThat(client.issueToken().token()).isEqualTo("token-1");

            assertThat(calls.get()).isEqualTo(1);
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void issueToken_rejectsBusinessFailure() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/oauth2/token", (request, response) -> response
                        .header("Content-Type", "application/json")
                        .sendString(reactor.core.publisher.Mono.just("""
                                {"token":"","return_code":1,"return_msg":"failed"}
                                """))))
                .bindNow();
        try {
            KiwoomAuthClient client = client(server);

            assertThatThrownBy(client::issueToken)
                    .isInstanceOf(MarketException.class)
                    .satisfies(e -> assertThat(((MarketException) e).errorCode())
                            .isEqualTo(MarketErrorCode.KIWOOM_BUSINESS_FAILED));
        } finally {
            server.disposeNow();
        }
    }

    private static KiwoomAuthClient client(DisposableServer server) {
        KiwoomAuthClient client = new KiwoomAuthClient(WebClient.builder()
                .baseUrl("http://localhost:" + server.port())
                .build());
        ReflectionTestUtils.setField(client, "appKey", "app");
        ReflectionTestUtils.setField(client, "secretKey", "secret");
        ReflectionTestUtils.setField(client, "tokenCacheTtl", Duration.ofMinutes(50));
        return client;
    }
}

package org.profit.candle.gateway.proxy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
public class ProxyWebFilter implements WebFilter, Ordered {

    // 프록시 시 전달하지 않을 HTTP hop-by-hop 헤더
    private static final Set<String> EXCLUDED_REQUEST_HEADERS = Set.of(
            "host", "connection", "keep-alive", "transfer-encoding", "upgrade"
    );
    private static final Set<String> EXCLUDED_RESPONSE_HEADERS = Set.of(
            "connection", "keep-alive", "transfer-encoding", "content-length", "upgrade"
    );

    private final WebClient webClient;
    private final String authServiceUri;
    private final String bffUri;

    public ProxyWebFilter(
            @Value("${gateway.route.auth-uri:http://localhost:8081}") String authServiceUri,
            @Value("${gateway.route.bff-uri:http://localhost:8080}") String bffUri) {
        this.webClient = WebClient.create();
        this.authServiceUri = authServiceUri;
        this.bffUri = bffUri;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getPath().value();
        String query = request.getURI().getRawQuery();

        String targetBase = path.startsWith("/api/v1/auth/") ? authServiceUri : bffUri;
        String targetUri = targetBase + path + (query != null ? "?" + query : "");

        HttpHeaders forwardHeaders = new HttpHeaders();
        request.getHeaders().forEach((name, values) -> {
            if (!EXCLUDED_REQUEST_HEADERS.contains(name.toLowerCase())) {
                forwardHeaders.put(name, values);
            }
        });

        return webClient.method(request.getMethod())
                .uri(targetUri)
                .headers(h -> h.addAll(forwardHeaders))
                .body(request.getBody(), DataBuffer.class)
                .exchangeToMono(clientResponse -> {
                    var response = exchange.getResponse();
                    response.setStatusCode(clientResponse.statusCode());
                    clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
                        if (!EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                            response.getHeaders().put(name, values);
                        }
                    });
                    Flux<DataBuffer> body = clientResponse.bodyToFlux(DataBuffer.class);
                    return response.writeWith(body);
                });
    }

    @Override
    public int getOrder() {
        return 100;
    }
}

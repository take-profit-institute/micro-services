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

import java.util.List;
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

        String targetBase;
        String targetPath;
        if (path.startsWith("/api/v1/auth/")) {
            // /api/v1/auth/** → auth-service, 경로 유지
            targetBase = authServiceUri;
            targetPath = path;
        } else if (isAuthServicePath(path)) {
            // /api/auth/{providers|oauth/**|token/refresh|logout} → auth-service, /api/v1/auth/** 재작성
            targetBase = authServiceUri;
            targetPath = "/api/v1" + path.substring("/api".length());
        } else {
            // /api/auth/me, /api/auth/token/validate 등 BFF 전용 경로
            targetBase = bffUri;
            targetPath = path;
        }
        String targetUri = targetBase + targetPath + (query != null ? "?" + query : "");

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
                    final String routedBase = targetBase;
                    clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
                        if (EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase())) return;
                        if (name.equalsIgnoreCase(HttpHeaders.SET_COOKIE) && routedBase.equals(authServiceUri)) {
                            response.getHeaders().put(name, rewriteAuthCookiePaths(values));
                        } else {
                            response.getHeaders().put(name, values);
                        }
                    });
                    Flux<DataBuffer> body = clientResponse.bodyToFlux(DataBuffer.class);
                    return response.writeWith(body);
                });
    }

    /** auth-service 내부 경로(/api/v1/auth)를 게이트웨이 공개 경로(/api/auth)로 재작성. */
    static List<String> rewriteAuthCookiePaths(List<String> values) {
        return values.stream()
                .map(v -> v.replace("Path=/api/v1/auth", "Path=/api/auth"))
                .toList();
    }

    /**
     * auth-service가 실제로 처리하는 경로만 true.
     * /me, /token/validate 등 BFF 전용 경로는 false → BFF로 라우팅.
     */
    private static boolean isAuthServicePath(String path) {
        return path.startsWith("/api/auth/oauth/")
                || path.equals("/api/auth/token/refresh")
                || path.equals("/api/auth/logout");
    }

    @Override
    public int getOrder() {
        return 100;
    }
}

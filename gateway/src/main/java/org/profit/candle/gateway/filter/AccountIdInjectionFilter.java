package org.profit.candle.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AccountIdInjectionFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> {
                    String accountId = auth.getToken().getSubject();
                    if (accountId == null) return chain.filter(exchange);
                    String role = auth.getToken().getClaimAsString("role");
                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> {
                                // header(name, value)는 클라이언트가 위조한 값을 덮어써 스푸핑을 막는다.
                                r.header("X-Account-Id", accountId);
                                if (role != null && !role.isBlank()) {
                                    r.header("X-Account-Role", role);
                                }
                            })
                            .build();
                    return chain.filter(mutated);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}

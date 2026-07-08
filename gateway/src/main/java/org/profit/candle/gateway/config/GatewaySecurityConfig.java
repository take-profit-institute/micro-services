package org.profit.candle.gateway.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    // auth-service 가 노출하는 JWKS(공개키) URI. RS256 검증. 대칭키 공유 폐지.
    @Value("${gateway.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${gateway.jwt.issuer:}")
    private String issuer;

    @Value("${gateway.jwt.audience:}")
    private String audience;

    @Value("${gateway.cors.allowed-origin-patterns:http://localhost:3000,http://localhost:3001}")
    private List<String> allowedOrigins;

    // auth 경로 + preflight: JWT 검증 없이 통과
    // /api/auth/me, /api/auth/token/validate 등 인증 필요 경로는 제외
    @Bean
    @Order(1)
    public SecurityWebFilterChain publicFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(new OrServerWebExchangeMatcher(
                        ServerWebExchangeMatchers.pathMatchers(HttpMethod.OPTIONS, "/**"),
                        ServerWebExchangeMatchers.pathMatchers(
                                "/**",
                                "/api/auth/providers",
                                "/api/auth/oauth/**",
                                "/api/auth/token/refresh",
                                "/api/auth/logout",
                                // admin 로그인은 토큰 발급 경로이므로 JWT 검증 없이 통과시킨다.
                                "/api/admin/login"
                        ),
                        ServerWebExchangeMatchers.pathMatchers("/api/v1/auth/**")
                ))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth.anyExchange().permitAll())
                .build();
    }

    // 나머지 경로: JWT 필수
    @Bean
    @Order(2)
    public SecurityWebFilterChain securedFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        // 관리자 계정 CRUD는 SUPER_ADMIN 전용
                        .pathMatchers("/api/v1/admin/**").hasAuthority("ROLE_SUPER_ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .bearerTokenConverter(bearerTokenConverter())
                )
                .build();
    }

    // JWT의 role claim(USER|ADMIN|SUPER_ADMIN)을 ROLE_* authority로 매핑한다.
    @Bean
    public Converter<Jwt, reactor.core.publisher.Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                return List.<GrantedAuthority>of();
            }
            Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            return authorities;
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // allowCredentials(true)와 와일드카드를 함께 쓰려면 setAllowedOrigins가 아닌
        // setAllowedOriginPatterns를 써야 한다. (vercel 프리뷰 도메인 매칭용)
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(HttpHeaders.AUTHORIZATION, "X-Account-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // RS256 + JWKS. auth-service /.well-known/jwks.json 에서 공개키를 받아 검증(캐시).
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (issuer != null && !issuer.isBlank()) {
            validators.add(new JwtIssuerValidator(issuer));
        }
        if (audience != null && !audience.isBlank()) {
            validators.add(new JwtClaimValidator<List<String>>(
                    JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    @Bean
    public ServerAuthenticationConverter bearerTokenConverter() {
        return exchange -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return Mono.just(new BearerTokenAuthenticationToken(authHeader.substring(7)));
            }
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst("access_token");
            if (cookie != null && !cookie.getValue().isBlank()) {
                return Mono.just(new BearerTokenAuthenticationToken(cookie.getValue()));
            }
            return Mono.empty();
        };
    }
}

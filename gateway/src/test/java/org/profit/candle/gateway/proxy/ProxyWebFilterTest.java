package org.profit.candle.gateway.proxy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyWebFilterTest {

    @Test
    void rewriteAuthCookiePaths_replacesV1AuthPathWithPublicPath() {
        List<String> input = List.of(
                "refresh_token=abc; Path=/api/v1/auth; HttpOnly; SameSite=Lax"
        );

        List<String> result = ProxyWebFilter.rewriteAuthCookiePaths(input);

        assertThat(result).containsExactly(
                "refresh_token=abc; Path=/api/auth; HttpOnly; SameSite=Lax"
        );
    }

    @Test
    void rewriteAuthCookiePaths_doesNotAlterCookiesWithOtherPaths() {
        List<String> input = List.of(
                "access_token=xyz; Path=/; HttpOnly"
        );

        List<String> result = ProxyWebFilter.rewriteAuthCookiePaths(input);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void rewriteAuthCookiePaths_handlesMixedCookieList() {
        List<String> input = List.of(
                "access_token=xyz; Path=/; HttpOnly",
                "refresh_token=abc; Path=/api/v1/auth; HttpOnly; SameSite=Lax"
        );

        List<String> result = ProxyWebFilter.rewriteAuthCookiePaths(input);

        assertThat(result).containsExactly(
                "access_token=xyz; Path=/; HttpOnly",
                "refresh_token=abc; Path=/api/auth; HttpOnly; SameSite=Lax"
        );
    }

    @Test
    void rewriteAuthCookiePaths_preservesAllCookieAttributes() {
        List<String> input = List.of(
                "refresh_token=tok; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000"
        );

        List<String> result = ProxyWebFilter.rewriteAuthCookiePaths(input);

        assertThat(result.get(0))
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Max-Age=2592000")
                .contains("Path=/api/auth");
    }

    @Test
    void rewriteAuthCookiePaths_emptyList_returnsEmpty() {
        assertThat(ProxyWebFilter.rewriteAuthCookiePaths(List.of())).isEmpty();
    }
}

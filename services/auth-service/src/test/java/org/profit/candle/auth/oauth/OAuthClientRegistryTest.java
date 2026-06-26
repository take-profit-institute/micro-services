package org.profit.candle.auth.oauth;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthClientRegistryTest {

    @Test
    void resolve_returnsMatchingClient() {
        OAuthClient google = clientWithProvider("google");
        OAuthClientRegistry registry = new OAuthClientRegistry(List.of(google));

        assertThat(registry.resolve("google")).isSameAs(google);
    }

    @Test
    void resolve_picksCorrectClientAmongMultiple() {
        OAuthClient google = clientWithProvider("google");
        OAuthClient kakao = clientWithProvider("kakao");
        OAuthClientRegistry registry = new OAuthClientRegistry(List.of(google, kakao));

        assertThat(registry.resolve("kakao")).isSameAs(kakao);
    }

    @Test
    void resolve_unknownProvider_throwsUnsupportedProvider() {
        OAuthClientRegistry registry = new OAuthClientRegistry(List.of(clientWithProvider("google")));

        AuthException ex = assertThrows(AuthException.class, () -> registry.resolve("naver"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }

    @Test
    void resolve_emptyRegistry_throwsUnsupportedProvider() {
        OAuthClientRegistry registry = new OAuthClientRegistry(List.of());

        AuthException ex = assertThrows(AuthException.class, () -> registry.resolve("google"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }

    private OAuthClient clientWithProvider(String name) {
        OAuthClient client = mock(OAuthClient.class);
        when(client.provider()).thenReturn(name);
        return client;
    }
}

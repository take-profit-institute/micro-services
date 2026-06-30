package org.profit.candle.auth.oauth;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthRedirectUriResolverTest {

    static final String WEB = "https://webapp-webapp.vercel.app/auth/google/callback";
    static final String BRIDGE = "https://webapp-webapp.vercel.app/auth/callback";

    final OAuthRedirectUriResolver resolver = new OAuthRedirectUriResolver(new AuthProperties(
            new AuthProperties.Google("cid", "sec", WEB, List.of(BRIDGE)),
            new AuthProperties.Kakao("cid", "sec", WEB, List.of()),
            new AuthProperties.Naver("cid", "sec", WEB, List.of()),
            null, null));

    @Test
    void resolve_blankRequested_returnsDefault() {
        assertThat(resolver.resolve("google", null)).isEqualTo(WEB);
        assertThat(resolver.resolve("google", "  ")).isEqualTo(WEB);
    }

    @Test
    void resolve_allowedRequested_returnsRequested() {
        assertThat(resolver.resolve("google", BRIDGE)).isEqualTo(BRIDGE);
    }

    @Test
    void resolve_defaultIsAlwaysAllowed() {
        assertThat(resolver.resolve("google", WEB)).isEqualTo(WEB);
    }

    @Test
    void resolve_notInAllowlist_throws() {
        AuthException ex = assertThrows(AuthException.class,
                () -> resolver.resolve("google", "https://evil.example.com/cb"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.OAUTH_REDIRECT_URI_NOT_ALLOWED);
    }

    @Test
    void resolve_providerWithoutAllowlist_rejectsNonDefault() {
        assertThat(resolver.resolve("kakao", WEB)).isEqualTo(WEB);
        AuthException ex = assertThrows(AuthException.class,
                () -> resolver.resolve("kakao", BRIDGE));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.OAUTH_REDIRECT_URI_NOT_ALLOWED);
    }

    @Test
    void resolve_unknownProvider_throws() {
        AuthException ex = assertThrows(AuthException.class, () -> resolver.resolve("unknown", null));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }
}

package org.profit.candle.auth.oauth.naver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NaverOAuthClientTest {

    @Mock AuthProperties properties;
    @Mock AuthProperties.Naver naver;
    @InjectMocks NaverOAuthClient client;

    @BeforeEach
    void setUp() {
        lenient().when(properties.naver()).thenReturn(naver);
    }

    @Test
    void provider_returnsNaver() {
        assertThat(client.provider()).isEqualTo("naver");
    }

    @Test
    void fetch_blankClientId_throwsConfigurationInvalid() {
        when(naver.clientId()).thenReturn("");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code", "state", null));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.NAVER_OAUTH_CONFIGURATION_INVALID);
    }

    @Test
    void fetch_blankClientSecret_throwsConfigurationInvalid() {
        when(naver.clientId()).thenReturn("client-id");
        when(naver.clientSecret()).thenReturn("  ");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code", "state", null));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.NAVER_OAUTH_CONFIGURATION_INVALID);
    }

    @Test
    void fetch_blankRedirectUri_throwsConfigurationInvalid() {
        when(naver.clientId()).thenReturn("client-id");
        when(naver.clientSecret()).thenReturn("secret");
        when(naver.redirectUri()).thenReturn("");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code", "state", null));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.NAVER_OAUTH_CONFIGURATION_INVALID);
    }

    @Test
    void fetch_blankState_throwsInvalidRequest_beforeExternalCall() {
        when(naver.clientId()).thenReturn("client-id");
        when(naver.clientSecret()).thenReturn("secret");
        when(naver.redirectUri()).thenReturn("https://example.com/callback");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code", "  ", null));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.INVALID_OAUTH_REQUEST);
    }

    @Test
    void fetch_nullState_throwsInvalidRequest() {
        when(naver.clientId()).thenReturn("client-id");
        when(naver.clientSecret()).thenReturn("secret");
        when(naver.redirectUri()).thenReturn("https://example.com/callback");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code", null, null));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.INVALID_OAUTH_REQUEST);
    }
}

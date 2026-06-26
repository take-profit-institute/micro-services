package org.profit.candle.auth.oauth.google;

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
class GoogleOAuthClientTest {

    @Mock AuthProperties properties;
    @Mock AuthProperties.Google google;
    @InjectMocks GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        lenient().when(properties.google()).thenReturn(google);
    }

    @Test
    void provider_returnsGoogle() {
        assertThat(client.provider()).isEqualTo("google");
    }

    @Test
    void fetch_blankClientId_throwsConfigurationInvalid() {
        when(google.clientId()).thenReturn("");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.GOOGLE_OAUTH_CONFIGURATION_INVALID);
    }

    @Test
    void fetch_blankClientSecret_throwsConfigurationInvalid() {
        when(google.clientId()).thenReturn("client-id");
        when(google.clientSecret()).thenReturn("  ");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.GOOGLE_OAUTH_CONFIGURATION_INVALID);
    }

    @Test
    void fetch_blankRedirectUri_throwsConfigurationInvalid() {
        when(google.clientId()).thenReturn("client-id");
        when(google.clientSecret()).thenReturn("secret");
        when(google.redirectUri()).thenReturn("");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.GOOGLE_OAUTH_CONFIGURATION_INVALID);
    }
}

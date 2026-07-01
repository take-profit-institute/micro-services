package org.profit.candle.auth.oauth.kakao;

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
class KakaoOAuthClientTest {

    @Mock AuthProperties properties;
    @Mock AuthProperties.Kakao kakao;
    @InjectMocks KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        lenient().when(properties.kakao()).thenReturn(kakao);
    }

    @Test
    void provider_returnsKakao() {
        assertThat(client.provider()).isEqualTo("kakao");
    }

    @Test
    void fetch_blankClientId_throwsConfigurationInvalid() {
        when(kakao.clientId()).thenReturn("");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code", "state", null));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.KAKAO_OAUTH_CONFIGURATION_INVALID);
    }

    @Test
    void fetch_blankClientSecret_throwsConfigurationInvalid() {
        when(kakao.clientId()).thenReturn("client-id");
        when(kakao.clientSecret()).thenReturn("  ");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code", "state", null));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.KAKAO_OAUTH_CONFIGURATION_INVALID);
    }

    @Test
    void fetch_blankRedirectUri_throwsConfigurationInvalid() {
        when(kakao.clientId()).thenReturn("client-id");
        when(kakao.clientSecret()).thenReturn("secret");
        when(kakao.redirectUri()).thenReturn("");

        AuthException ex = assertThrows(AuthException.class, () -> client.fetch("code", "state", null));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.KAKAO_OAUTH_CONFIGURATION_INVALID);
    }
}

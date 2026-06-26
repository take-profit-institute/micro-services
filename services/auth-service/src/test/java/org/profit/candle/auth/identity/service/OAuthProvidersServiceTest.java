package org.profit.candle.auth.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.api.dto.ProvidersResponse;
import org.profit.candle.auth.config.AuthProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthProvidersServiceTest {

    @Mock AuthProperties properties;
    @Mock AuthProperties.Google google;

    @InjectMocks OAuthProvidersService service;

    @BeforeEach
    void setUp() {
        when(properties.google()).thenReturn(google);
        when(google.clientId()).thenReturn("test-client-id");
        when(google.redirectUri()).thenReturn("http://localhost:3000/auth/google/callback");
    }

    @Test
    void listProviders_returnsOneGoogleProvider() {
        ProvidersResponse response = service.listProviders();

        assertThat(response.providers()).hasSize(1);
        assertThat(response.providers().get(0).name()).isEqualTo("google");
    }

    @Test
    void listProviders_authorizationUrlContainsClientId() {
        ProvidersResponse response = service.listProviders();

        assertThat(response.providers().get(0).authorizationUrl())
                .contains("client_id=test-client-id");
    }

    @Test
    void googleAuthorizationUrl_hasRequiredOAuthParams() {
        String url = service.googleAuthorizationUrl();

        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("scope=");
        assertThat(url).contains("access_type=offline");
        assertThat(url).contains("prompt=consent");
    }

    @Test
    void googleAuthorizationUrl_encodesRedirectUri() {
        String url = service.googleAuthorizationUrl();

        // 콜론·슬래시가 퍼센트 인코딩돼야 함
        assertThat(url).doesNotContain("redirect_uri=http://localhost");
        assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost");
    }
}

package org.profit.candle.auth.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.api.dto.ProviderResponse;
import org.profit.candle.auth.api.dto.ProvidersResponse;
import org.profit.candle.auth.config.AuthProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthProvidersServiceTest {

    @Mock AuthProperties properties;
    @Mock AuthProperties.Google google;
    @Mock AuthProperties.Kakao kakao;
    @Mock AuthProperties.Naver naver;

    @InjectMocks OAuthProvidersService service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.google()).thenReturn(google);
        lenient().when(google.clientId()).thenReturn("test-client-id");
        lenient().when(google.redirectUri()).thenReturn("http://localhost:3000/auth/google/callback");

        lenient().when(properties.kakao()).thenReturn(kakao);
        lenient().when(kakao.clientId()).thenReturn("test-kakao-id");
        lenient().when(kakao.redirectUri()).thenReturn("http://localhost:3000/auth/kakao/callback");

        lenient().when(properties.naver()).thenReturn(naver);
        lenient().when(naver.clientId()).thenReturn("test-naver-id");
        lenient().when(naver.redirectUri()).thenReturn("http://localhost:3000/auth/naver/callback");
    }

    @Test
    void listProviders_returnsAllProviders() {
        ProvidersResponse response = service.listProviders();

        assertThat(response.providers()).hasSize(3);
        assertThat(response.providers()).extracting(ProviderResponse::name)
                .containsExactly("google", "kakao", "naver");
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

    @Test
    void kakaoAuthorizationUrl_hasRequiredOAuthParams() {
        String url = service.kakaoAuthorizationUrl();

        assertThat(url).startsWith("https://kauth.kakao.com/oauth/authorize");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=test-kakao-id");
        assertThat(url).contains("scope=account_email");
    }

    @Test
    void naverAuthorizationUrl_hasRequiredOAuthParams() {
        String url = service.naverAuthorizationUrl();

        assertThat(url).startsWith("https://nid.naver.com/oauth2.0/authorize");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=test-naver-id");
    }

    @Test
    void naverAuthorizationUrl_omitsState_frontendAppendsIt() {
        String url = service.naverAuthorizationUrl();

        // state는 CSRF 방지를 위해 프론트가 생성·검증하므로 백엔드 URL엔 포함하지 않는다.
        assertThat(url).doesNotContain("state=");
    }
}

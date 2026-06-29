package org.profit.candle.auth.oauth.kakao;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.oauth.OAuthClient;
import org.profit.candle.auth.oauth.OAuthProfile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class KakaoOAuthClient implements OAuthClient {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final AuthProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public String provider() {
        return "kakao";
    }

    @Override
    public OAuthProfile fetch(String authorizationCode, String state) {
        validateConfiguration();
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("code", authorizationCode);
            form.add("client_id", properties.kakao().clientId());
            form.add("client_secret", properties.kakao().clientSecret());
            form.add("redirect_uri", properties.kakao().redirectUri());
            form.add("grant_type", "authorization_code");

            Map<?, ?> token = restClient.post().uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);

            String accessToken = String.valueOf(token.get("access_token"));

            Map<?, ?> profile = restClient.get().uri(USER_INFO_URI)
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(Map.class);

            Object account = profile.get("kakao_account");
            Map<?, ?> kakaoAccount = account instanceof Map<?, ?> map ? map : Map.of();

            return new OAuthProfile(
                    String.valueOf(profile.get("id")),
                    String.valueOf(kakaoAccount.get("email")),
                    Boolean.parseBoolean(String.valueOf(kakaoAccount.get("is_email_verified"))));
        } catch (RestClientException e) {
            throw new AuthException(AuthErrorCode.KAKAO_OAUTH_EXCHANGE_FAILED, e);
        }
    }

    private void validateConfiguration() {
        if (properties.kakao().clientId().isBlank() || properties.kakao().clientSecret().isBlank()
                || properties.kakao().redirectUri().isBlank()) {
            throw new AuthException(AuthErrorCode.KAKAO_OAUTH_CONFIGURATION_INVALID);
        }
    }
}

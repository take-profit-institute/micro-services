package org.profit.candle.auth.oauth.naver;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.oauth.OAuthClient;
import org.profit.candle.auth.oauth.OAuthProfile;
import org.profit.candle.auth.oauth.OAuthResponses;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class NaverOAuthClient implements OAuthClient {

    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_INFO_URI = "https://openapi.naver.com/v1/nid/me";

    private final AuthProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public String provider() {
        return "naver";
    }

    @Override
    public OAuthProfile fetch(String authorizationCode, String state, String redirectUri) {
        validateConfiguration(redirectUri);
        // naver는 토큰 교환에 state가 필수다. 누락/공백이면 외부 호출 전에 fail-fast 한다.
        if (state == null || state.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_REQUEST);
        }
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("code", authorizationCode);
            form.add("client_id", properties.naver().clientId());
            form.add("client_secret", properties.naver().clientSecret());
            form.add("redirect_uri", redirectUri);
            form.add("grant_type", "authorization_code");
            // naver는 토큰 교환 시 authorize에 사용한 state를 함께 요구한다.
            form.add("state", state);

            Map<?, ?> token = restClient.post().uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);

            String accessToken = OAuthResponses.requireString(
                    token, "access_token", AuthErrorCode.NAVER_OAUTH_EXCHANGE_FAILED);

            Map<?, ?> body = restClient.get().uri(USER_INFO_URI)
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(Map.class);

            Object response = OAuthResponses.requireBody(body, AuthErrorCode.NAVER_OAUTH_EXCHANGE_FAILED).get("response");
            Map<?, ?> profile = response instanceof Map<?, ?> map ? map : Map.of();

            String subject = OAuthResponses.requireString(
                    profile, "id", AuthErrorCode.NAVER_OAUTH_EXCHANGE_FAILED);
            Object email = profile.get("email");
            return new OAuthProfile(
                    subject,
                    email == null ? null : email.toString(),
                    email != null);
        } catch (RestClientException e) {
            throw new AuthException(AuthErrorCode.NAVER_OAUTH_EXCHANGE_FAILED, e);
        }
    }

    private void validateConfiguration(String redirectUri) {
        if (properties.naver().clientId().isBlank() || properties.naver().clientSecret().isBlank()
                || redirectUri == null || redirectUri.isBlank()) {
            throw new AuthException(AuthErrorCode.NAVER_OAUTH_CONFIGURATION_INVALID);
        }
    }
}

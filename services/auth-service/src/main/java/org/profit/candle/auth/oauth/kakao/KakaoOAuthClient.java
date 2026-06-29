package org.profit.candle.auth.oauth.kakao;

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
public class KakaoOAuthClient implements OAuthClient {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String EMAIL_SUFFIX = "@kakao.com";

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

            String accessToken = OAuthResponses.requireString(
                    token, "access_token", AuthErrorCode.KAKAO_OAUTH_EXCHANGE_FAILED);

            Map<?, ?> profile = restClient.get().uri(USER_INFO_URI)
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(Map.class);

            // 모킹앱에선 account_email 동의 항목을 쓸 수 없어 profile_nickname 스코프만 요청한다.
            // 카카오 계정 이메일을 받지 못하므로, 항상 존재하고 유니크한 카카오 id로 합성 이메일을 만든다.
            // 계정 식별은 provider+subject(id)로 이뤄지므로 합성 이메일은 토큰/표시용으로만 쓰인다.
            String subject = OAuthResponses.requireString(
                    profile, "id", AuthErrorCode.KAKAO_OAUTH_EXCHANGE_FAILED);
            return new OAuthProfile(subject, subject + EMAIL_SUFFIX, true);
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

package org.profit.candle.auth.google.client;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.util.LinkedMultiValueMap;

@Component
@RequiredArgsConstructor
public class GoogleRestClient implements GoogleOAuthClient {
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";

    private final AuthProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public GoogleProfile exchangeAuthorizationCode(String authorizationCode) {
        validateConfiguration();
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("code", authorizationCode);
            form.add("client_id", properties.google().clientId());
            form.add("client_secret", properties.google().clientSecret());
            form.add("redirect_uri", properties.google().redirectUri());
            form.add("grant_type", "authorization_code");
            Map<?, ?> token = restClient.post().uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            String accessToken = String.valueOf(token.get("access_token"));
            Map<?, ?> profile = restClient.get().uri(USER_INFO_URI)
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(Map.class);
            return new GoogleProfile(String.valueOf(profile.get("sub")), String.valueOf(profile.get("email")),
                    Boolean.parseBoolean(String.valueOf(profile.get("email_verified"))));
        } catch (RestClientException exception) {
            throw new AuthException(AuthErrorCode.GOOGLE_OAUTH_EXCHANGE_FAILED, exception);
        }
    }

    private void validateConfiguration() {
        if (properties.google().clientId().isBlank() || properties.google().clientSecret().isBlank()
                || properties.google().redirectUri().isBlank()) {
            throw new AuthException(AuthErrorCode.GOOGLE_OAUTH_CONFIGURATION_INVALID);
        }
    }
}

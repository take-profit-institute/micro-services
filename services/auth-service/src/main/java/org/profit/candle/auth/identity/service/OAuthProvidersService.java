package org.profit.candle.auth.identity.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.api.dto.ProviderResponse;
import org.profit.candle.auth.api.dto.ProvidersResponse;
import org.profit.candle.auth.config.AuthProperties;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthProvidersService {

    private final AuthProperties properties;

    public ProvidersResponse listProviders() {
        return ProvidersResponse.of(List.of(
                new ProviderResponse("google", googleAuthorizationUrl()),
                new ProviderResponse("kakao", kakaoAuthorizationUrl()),
                new ProviderResponse("naver", naverAuthorizationUrl())));
    }

    public String googleAuthorizationUrl() {
        return "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id="
                + encode(properties.google().clientId()) + "&redirect_uri=" + encode(properties.google().redirectUri())
                + "&scope=" + encode("openid email profile") + "&access_type=offline&prompt=consent";
    }

    public String kakaoAuthorizationUrl() {
        return "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id="
                + encode(properties.kakao().clientId()) + "&redirect_uri=" + encode(properties.kakao().redirectUri())
                + "&scope=" + encode("profile_nickname");
    }

    // state는 CSRF 방지를 위해 프론트가 생성·검증한다. 프론트가 이 URL에 &state=... 를 덧붙이며,
    // naver는 authorize/토큰 교환 모두 state를 요구하므로 콜백 시 동일한 state를 함께 보내야 한다.
    public String naverAuthorizationUrl() {
        return "https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id="
                + encode(properties.naver().clientId()) + "&redirect_uri=" + encode(properties.naver().redirectUri());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

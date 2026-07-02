package org.profit.candle.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Google google, Kakao kakao, Naver naver, Jwt jwt, Cookies cookies) {
    // allowedRedirectUris: 토큰 교환에 사용을 허용할 redirect_uri 목록.
    // 클라이언트(웹/앱)가 보낸 redirect_uri가 이 목록이나 redirectUri(기본값)에 있을 때만 통과시킨다.
    public record Google(String clientId, String clientSecret, String redirectUri,
            List<String> allowedRedirectUris) {
    }

    public record Kakao(String clientId, String clientSecret, String redirectUri,
            List<String> allowedRedirectUris) {
    }

    public record Naver(String clientId, String clientSecret, String redirectUri,
            List<String> allowedRedirectUris) {
    }

    public record Jwt(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl, String hmacSecret) {
    }

    public record Cookies(String domain, boolean secure, String sameSite) {
    }
}

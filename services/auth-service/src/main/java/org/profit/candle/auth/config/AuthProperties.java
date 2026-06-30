package org.profit.candle.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Google google, Kakao kakao, Naver naver, Jwt jwt, Cookies cookies) {
    // redirectUri: 기본값(웹). allowedRedirectUris: 요청이 override 가능한 redirect 허용목록(네이티브 브릿지 등).
    public record Google(String clientId, String clientSecret, String redirectUri, List<String> allowedRedirectUris) {
    }

    public record Kakao(String clientId, String clientSecret, String redirectUri, List<String> allowedRedirectUris) {
    }

    public record Naver(String clientId, String clientSecret, String redirectUri, List<String> allowedRedirectUris) {
    }

    public record Jwt(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl, String hmacSecret) {
    }

    public record Cookies(String domain, boolean secure, String sameSite) {
    }
}

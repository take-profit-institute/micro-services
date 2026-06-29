package org.profit.candle.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Google google, Kakao kakao, Naver naver, Jwt jwt, Cookies cookies) {
    public record Google(String clientId, String clientSecret, String redirectUri) {
    }

    public record Kakao(String clientId, String clientSecret, String redirectUri) {
    }

    public record Naver(String clientId, String clientSecret, String redirectUri) {
    }

    public record Jwt(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl, String hmacSecret) {
    }

    public record Cookies(String domain, boolean secure, String sameSite) {
    }
}

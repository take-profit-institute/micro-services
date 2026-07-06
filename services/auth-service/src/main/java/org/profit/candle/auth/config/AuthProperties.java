package org.profit.candle.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Google google, Kakao kakao, Naver naver, Jwt jwt, Cookies cookies, Admin admin) {
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

    // issuer: OIDC issuer (HTTPS URL, 토큰 iss·JWKS 노출 도메인과 일치). audience: 단일 API audience.
    // privateKey: RS256 서명용 RSA 개인키(PEM PKCS#8). 비면 dev용 임시 키를 기동 시 생성한다.
    public record Jwt(String issuer, String audience, Duration accessTokenTtl, Duration refreshTokenTtl,
            String privateKey) {
    }

    public record Cookies(String domain, boolean secure, String sameSite) {
    }

    // maxFailedAttempts: 로그인 연속 실패 임계치. 초과 시 lockDuration 동안 계정 잠금.
    // bootstrap: 기동 시 관리자 계정이 0건이고 username/password가 설정되어 있으면 SUPER_ADMIN 1개 생성.
    public record Admin(Bootstrap bootstrap, int maxFailedAttempts, Duration lockDuration) {
        public record Bootstrap(String username, String password, String displayName) {
        }
    }
}

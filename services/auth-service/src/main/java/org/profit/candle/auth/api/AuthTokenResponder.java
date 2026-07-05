package org.profit.candle.auth.api;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.api.dto.OAuthLoginResponse;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.token.service.IssuedTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * access/refresh 토큰을 httpOnly 쿠키 + 응답 body로 내려주는 공통 로직.
 * OAuth 로그인과 관리자 로그인이 동일한 쿠키 규약을 공유한다.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenResponder {

    private final AuthProperties properties;

    public ResponseEntity<OAuthLoginResponse> tokenResponse(IssuedTokens tokens, boolean isNewUser) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("access_token", tokens.accessToken(), "/",
                        properties.jwt().accessTokenTtl()).toString())
                .header(HttpHeaders.SET_COOKIE, cookie("refresh_token", tokens.refreshToken(), "/api/v1/auth",
                        properties.jwt().refreshTokenTtl()).toString())
                .body(new OAuthLoginResponse(
                        tokens.accessToken(),
                        tokens.refreshToken(),
                        properties.jwt().accessTokenTtl().toSeconds(),
                        properties.jwt().refreshTokenTtl().toSeconds(),
                        isNewUser));
    }

    public ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value).httpOnly(true)
                .secure(properties.cookies().secure()).sameSite(properties.cookies().sameSite())
                .path(path).maxAge(maxAge);
        if (!properties.cookies().domain().isBlank()) {
            builder.domain(properties.cookies().domain());
        }
        return builder.build();
    }

    public ResponseCookie expiredCookie(String name, String path) {
        return cookie(name, "", path, Duration.ZERO);
    }
}

package org.profit.candle.auth.api;

import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.api.dto.OAuthLoginRequest;
import org.profit.candle.auth.api.dto.OAuthLoginResponse;
import org.profit.candle.auth.api.dto.ProvidersResponse;
import org.profit.candle.auth.api.dto.RefreshTokenRequest;
import org.profit.candle.auth.identity.service.OAuthLoginService;
import org.profit.candle.auth.identity.service.LoginResult;
import org.profit.candle.auth.identity.service.OAuthProvidersService;
import org.profit.candle.auth.token.service.RefreshTokenService;
import org.profit.candle.auth.token.service.IssuedTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthProperties properties;
    private final OAuthLoginService oAuthLoginService;
    private final RefreshTokenService refreshTokenService;
    private final OAuthProvidersService oAuthProvidersService;

    @GetMapping("/providers")
    public ProvidersResponse listProviders() {
        return oAuthProvidersService.listProviders();
    }

    @PostMapping("/oauth/{provider}")
    public ResponseEntity<OAuthLoginResponse> login(@PathVariable String provider,
            @RequestBody OAuthLoginRequest request) {
        if (request.authorizationCode() == null || request.authorizationCode().isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_REQUEST);
        }
        LoginResult result = oAuthLoginService.login(provider, request.authorizationCode(), request.state());
        return tokenResponse(result.tokens(), result.isNewUser());
    }

    // refresh token은 웹(httpOnly 쿠키)과 모바일/네이티브 앱(요청 body) 양쪽에서 받는다.
    // 쿠키를 쓸 수 없는 Capacitor/WebView 앱은 보안 저장소의 토큰을 body로 전달한다.
    @PostMapping("/token/refresh")
    public ResponseEntity<OAuthLoginResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest body) {
        String refreshToken = resolveRefreshToken(cookieToken, body);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        return tokenResponse(refreshTokenService.rotate(refreshToken), false);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest body) {
        String refreshToken = resolveRefreshToken(cookieToken, body);
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie("access_token", "/").toString())
                .header(HttpHeaders.SET_COOKIE, expiredCookie("refresh_token", "/api/v1/auth").toString())
                .build();
    }

    // body의 토큰을 우선하고, 없으면 쿠키로 폴백한다.
    private String resolveRefreshToken(String cookieToken, RefreshTokenRequest body) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return body.refreshToken();
        }
        return cookieToken;
    }

    private ResponseEntity<OAuthLoginResponse> tokenResponse(IssuedTokens tokens, boolean isNewUser) {
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

    private ResponseCookie cookie(String name, String value, String path, java.time.Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value).httpOnly(true)
                .secure(properties.cookies().secure()).sameSite(properties.cookies().sameSite())
                .path(path).maxAge(maxAge);
        if (!properties.cookies().domain().isBlank()) {
            builder.domain(properties.cookies().domain());
        }
        return builder.build();
    }

    private ResponseCookie expiredCookie(String name, String path) {
        return cookie(name, "", path, java.time.Duration.ZERO);
    }

}

package org.profit.candle.auth.api;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.api.dto.ProviderResponse;
import org.profit.candle.auth.api.dto.ProvidersResponse;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.identity.service.LoginResult;
import org.profit.candle.auth.identity.service.OAuthLoginService;
import org.profit.candle.auth.identity.service.OAuthProvidersService;
import org.profit.candle.auth.token.service.IssuedTokens;
import org.profit.candle.auth.token.service.RefreshTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthProperties authProperties;
    @Mock OAuthLoginService oAuthLoginService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock OAuthProvidersService oAuthProvidersService;
    @InjectMocks AuthController controller;

    MockMvc mockMvc;

    static final IssuedTokens STUB_TOKENS = new IssuedTokens("access-token", "refresh-token", 3600L);

    @BeforeEach
    void setUp() {
        lenient().when(authProperties.jwt()).thenReturn(
                new AuthProperties.Jwt("candle", Duration.ofHours(1), Duration.ofDays(7), "hmac-secret"));
        lenient().when(authProperties.cookies()).thenReturn(
                new AuthProperties.Cookies("", false, "Lax"));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void listProviders_returnsProviderList() throws Exception {
        when(oAuthProvidersService.listProviders()).thenReturn(
                new ProvidersResponse(List.of(new ProviderResponse("google", "https://accounts.google.com"))));

        mockMvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[0].name").value("google"));
    }

    @Test
    void login_blankAuthorizationCode_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_nullAuthorizationCode_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCode_returns200WithTokenInBody() throws Exception {
        when(oAuthLoginService.login("google", "valid-code", null, null)).thenReturn(
                new LoginResult(STUB_TOKENS, false));

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\": \"valid-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void login_newUser_isNewUserTrueInResponse() throws Exception {
        when(oAuthLoginService.login("google", "code", null, null)).thenReturn(
                new LoginResult(STUB_TOKENS, true));

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\": \"code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true));
    }

    @Test
    void login_setsAccessAndRefreshCookies() throws Exception {
        when(oAuthLoginService.login("google", "code", null, null)).thenReturn(
                new LoginResult(STUB_TOKENS, false));

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\": \"code\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withValidCookie_returns200() throws Exception {
        when(refreshTokenService.rotate("valid-refresh-token")).thenReturn(STUB_TOKENS);

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                .cookie(new Cookie("refresh_token", "valid-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void refresh_withBodyToken_returns200() throws Exception {
        when(refreshTokenService.rotate("body-refresh-token")).thenReturn(STUB_TOKENS);

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"body-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void refresh_bodyTokenTakesPrecedenceOverCookie() throws Exception {
        when(refreshTokenService.rotate("body-refresh-token")).thenReturn(STUB_TOKENS);

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                .cookie(new Cookie("refresh_token", "cookie-refresh-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"body-refresh-token\"}"))
                .andExpect(status().isOk());

        verify(refreshTokenService).rotate("body-refresh-token");
    }

    @Test
    void refresh_blankBodyToken_fallsBackToCookie() throws Exception {
        when(refreshTokenService.rotate("cookie-refresh-token")).thenReturn(STUB_TOKENS);

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                .cookie(new Cookie("refresh_token", "cookie-refresh-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"\"}"))
                .andExpect(status().isOk());

        verify(refreshTokenService).rotate("cookie-refresh-token");
    }

    @Test
    void logout_withBodyToken_revokesTokenAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"body-refresh-token\"}"))
                .andExpect(status().isNoContent());

        verify(refreshTokenService).revoke("body-refresh-token");
    }

    @Test
    void logout_withCookie_revokesTokenAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isNoContent());

        verify(refreshTokenService).revoke("old-refresh-token");
    }

    @Test
    void logout_withoutCookie_returns204WithoutRevoke() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());

        verify(refreshTokenService, never()).revoke(any());
    }

    @Test
    void logout_setsExpiredCookiesInResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }
}

package org.profit.candle.auth.token.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.admin.repository.AdminAccountRepository;
import org.profit.candle.auth.identity.entity.OAuthAccount;
import org.profit.candle.auth.identity.repository.OAuthAccountRepository;
import org.profit.candle.auth.token.entity.PrincipalType;
import org.profit.candle.auth.token.entity.RefreshToken;
import org.profit.candle.auth.token.repository.RefreshTokenRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuthTokenServiceTest {

    @Mock AuthProperties properties;
    @Mock JwtEncoder jwtEncoder;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock OAuthAccountRepository oAuthAccountRepository;
    @Mock AdminAccountRepository adminAccountRepository;
    @InjectMocks DefaultAuthTokenService service;

    static final UUID USER_ID = UUID.randomUUID();
    static final String EMAIL = "user@example.com";
    static final Duration ACCESS_TTL = Duration.ofHours(1);
    static final Duration REFRESH_TTL = Duration.ofDays(7);

    @BeforeEach
    void setUp() {
        lenient().when(properties.jwt()).thenReturn(
                new AuthProperties.Jwt("candle", ACCESS_TTL, REFRESH_TTL, "secret"));

        Jwt stubJwt = Jwt.withTokenValue("stub.jwt.token")
                .header("alg", "HS256")
                .claim("sub", USER_ID.toString())
                .build();
        lenient().when(jwtEncoder.encode(any())).thenReturn(stubJwt);
    }

    @Test
    void issue_returnsAccessTokenFromEncoder() {
        IssuedTokens tokens = service.issue(USER_ID, EMAIL, "USER", PrincipalType.USER);
        assertThat(tokens.accessToken()).isEqualTo("stub.jwt.token");
    }

    @Test
    void issue_returnsNonBlankRefreshToken() {
        IssuedTokens tokens = service.issue(USER_ID, EMAIL, "USER", PrincipalType.USER);
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void issue_accessTokenTtlMatchesProperty() {
        IssuedTokens tokens = service.issue(USER_ID, EMAIL, "USER", PrincipalType.USER);
        assertThat(tokens.expiresInSeconds()).isEqualTo(ACCESS_TTL.toSeconds());
    }

    @Test
    void issue_savesRefreshTokenToRepository() {
        service.issue(USER_ID, EMAIL, "USER", PrincipalType.USER);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void issue_encodesUserIdAsSubject() {
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        service.issue(USER_ID, EMAIL, "USER", PrincipalType.USER);

        verify(jwtEncoder).encode(captor.capture());
        assertThat(captor.getValue().getClaims().getSubject()).isEqualTo(USER_ID.toString());
    }

    @Test
    void issue_encodesEmailClaim() {
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        service.issue(USER_ID, EMAIL, "USER", PrincipalType.USER);

        verify(jwtEncoder).encode(captor.capture());
        assertThat((String) captor.getValue().getClaims().getClaim("email")).isEqualTo(EMAIL);
    }

    @Test
    void rotate_validToken_returnsNewTokens() {
        RefreshToken stored = new RefreshToken(UUID.randomUUID(), USER_ID, "any-hash", PrincipalType.USER,
                Instant.now().plus(REFRESH_TTL));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(oAuthAccountRepository.findById(USER_ID)).thenReturn(
                Optional.of(new OAuthAccount(USER_ID, "google", "sub", EMAIL, Instant.now())));

        IssuedTokens tokens = service.rotate("valid-token");

        assertThat(tokens.accessToken()).isEqualTo("stub.jwt.token");
    }

    @Test
    void rotate_validToken_revokesOldToken() {
        RefreshToken stored = new RefreshToken(UUID.randomUUID(), USER_ID, "any-hash", PrincipalType.USER,
                Instant.now().plus(REFRESH_TTL));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(oAuthAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.rotate("valid-token");

        assertThat(stored.usableAt(Instant.now())).isFalse();
    }

    @Test
    void rotate_expiredToken_throwsInvalidRefreshToken() {
        RefreshToken expired = new RefreshToken(UUID.randomUUID(), USER_ID, "any-hash", PrincipalType.USER,
                Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        AuthException ex = assertThrows(AuthException.class, () -> service.rotate("expired-token"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void rotate_revokedToken_throwsInvalidRefreshToken() {
        RefreshToken revoked = new RefreshToken(UUID.randomUUID(), USER_ID, "any-hash", PrincipalType.USER,
                Instant.now().plus(REFRESH_TTL));
        revoked.revoke(Instant.now());
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        AuthException ex = assertThrows(AuthException.class, () -> service.rotate("revoked-token"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void rotate_tokenNotFound_throwsInvalidRefreshToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        AuthException ex = assertThrows(AuthException.class, () -> service.rotate("unknown-token"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void revoke_existingToken_revokesIt() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), USER_ID, "any-hash", PrincipalType.USER,
                Instant.now().plus(REFRESH_TTL));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        service.revoke("some-token");

        assertThat(token.usableAt(Instant.now())).isFalse();
    }

    @Test
    void revoke_tokenNotFound_doesNothing() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatCode(() -> service.revoke("unknown-token")).doesNotThrowAnyException();
    }
}

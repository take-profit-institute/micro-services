package org.profit.candle.auth.identity.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.event.OutboxWriter;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.identity.entity.OAuthAccount;
import org.profit.candle.auth.identity.repository.OAuthAccountRepository;
import org.profit.candle.auth.oauth.OAuthClient;
import org.profit.candle.auth.oauth.OAuthClientRegistry;
import org.profit.candle.auth.oauth.OAuthProfile;
import org.profit.candle.auth.token.service.AuthTokenIssuer;
import org.profit.candle.auth.token.service.IssuedTokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultOAuthLoginServiceTest {

    @Mock OAuthClientRegistry registry;
    @Mock OAuthAccountRepository accountRepository;
    @Mock AuthTokenIssuer tokenIssuer;
    @Mock OutboxWriter outboxWriter;
    @InjectMocks DefaultOAuthLoginService service;

    OAuthClient oAuthClient;
    static final IssuedTokens STUB_TOKENS = new IssuedTokens("access", "refresh", 3600);

    @BeforeEach
    void setUp() {
        oAuthClient = mock(OAuthClient.class);
        lenient().when(registry.resolve("google")).thenReturn(oAuthClient);
    }

    @Test
    void login_newUser_createsAccountAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        OAuthAccount saved = account(userId, "google", "sub123", "user@example.com");
        when(oAuthClient.fetch("code")).thenReturn(verifiedProfile("sub123", "user@example.com"));
        when(accountRepository.findByProviderAndProviderSubject("google", "sub123")).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenReturn(saved);
        when(tokenIssuer.issue(userId, "user@example.com")).thenReturn(STUB_TOKENS);

        LoginResult result = service.login("google", "code");

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.tokens()).isEqualTo(STUB_TOKENS);
        verify(accountRepository).save(any());
        verify(outboxWriter).recordUserCreated(userId, "user@example.com");
    }

    @Test
    void login_existingUser_skipsCreationAndEvent() {
        UUID userId = UUID.randomUUID();
        OAuthAccount existing = account(userId, "google", "sub123", "user@example.com");
        when(oAuthClient.fetch("code")).thenReturn(verifiedProfile("sub123", "user@example.com"));
        when(accountRepository.findByProviderAndProviderSubject("google", "sub123")).thenReturn(Optional.of(existing));
        when(tokenIssuer.issue(userId, "user@example.com")).thenReturn(STUB_TOKENS);

        LoginResult result = service.login("google", "code");

        assertThat(result.isNewUser()).isFalse();
        verify(accountRepository, never()).save(any());
        verify(outboxWriter, never()).recordUserCreated(any(), any());
    }

    @Test
    void login_unverifiedEmail_throwsAccountNotVerified() {
        when(oAuthClient.fetch("code")).thenReturn(new OAuthProfile("sub123", "user@example.com", false));

        AuthException ex = assertThrows(AuthException.class, () -> service.login("google", "code"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.OAUTH_ACCOUNT_NOT_VERIFIED);
    }

    @Test
    void login_blankSubject_throwsAccountNotVerified() {
        when(oAuthClient.fetch("code")).thenReturn(new OAuthProfile("", "user@example.com", true));

        AuthException ex = assertThrows(AuthException.class, () -> service.login("google", "code"));
        assertThat(ex.errorCode()).isEqualTo(AuthErrorCode.OAUTH_ACCOUNT_NOT_VERIFIED);
    }

    @Test
    void login_providerIsPassedThroughToRegistry() {
        OAuthClient kakao = mock(OAuthClient.class);
        when(registry.resolve("kakao")).thenReturn(kakao);
        UUID userId = UUID.randomUUID();
        OAuthAccount saved = account(userId, "kakao", "sub-kakao", "user@example.com");
        when(kakao.fetch("code")).thenReturn(verifiedProfile("sub-kakao", "user@example.com"));
        when(accountRepository.findByProviderAndProviderSubject("kakao", "sub-kakao")).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenReturn(saved);
        when(tokenIssuer.issue(userId, "user@example.com")).thenReturn(STUB_TOKENS);

        LoginResult result = service.login("kakao", "code");

        assertThat(result.isNewUser()).isTrue();
        verify(registry).resolve("kakao");
    }

    private OAuthProfile verifiedProfile(String subject, String email) {
        return new OAuthProfile(subject, email, true);
    }

    private OAuthAccount account(UUID userId, String provider, String subject, String email) {
        return new OAuthAccount(userId, provider, subject, email, Instant.now());
    }
}

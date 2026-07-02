package org.profit.candle.auth.identity.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.identity.entity.OAuthAccount;
import org.profit.candle.auth.identity.repository.OAuthAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuthMeServiceTest {

    @Mock OAuthAccountRepository oAuthAccountRepository;
    @InjectMocks DefaultAuthMeService authMeService;

    @Test
    void getMe_returnsAuthUser() {
        UUID userId = UUID.randomUUID();
        OAuthAccount account = new OAuthAccount(userId, "google", "sub-1", "user@example.com", Instant.now());
        when(oAuthAccountRepository.findById(userId)).thenReturn(Optional.of(account));

        AuthUserResult result = authMeService.getMe(userId.toString());

        assertThat(result.userId()).isEqualTo(userId.toString());
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.provider()).isEqualTo("google");
        assertThat(result.providerSubject()).isEqualTo("sub-1");
    }

    @Test
    void getMe_missingAccount_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        when(oAuthAccountRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authMeService.getMe(userId.toString()))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).errorCode())
                .isEqualTo(AuthErrorCode.AUTH_USER_NOT_FOUND);
    }

    @Test
    void getMe_invalidUserId_throwsInvalidUserId() {
        assertThatThrownBy(() -> authMeService.getMe("bad"))
                .isInstanceOf(AuthException.class)
                .extracting(exception -> ((AuthException) exception).errorCode())
                .isEqualTo(AuthErrorCode.INVALID_AUTH_USER_ID);
    }
}

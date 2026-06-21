package org.profit.candle.auth.identity.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.google.client.GoogleOAuthClient;
import org.profit.candle.auth.google.client.GoogleProfile;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.identity.entity.OAuthAccount;
import org.profit.candle.auth.identity.repository.OAuthAccountRepository;
import org.profit.candle.auth.token.service.AuthTokenIssuer;
import org.profit.candle.auth.token.service.IssuedTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultGoogleLoginService implements GoogleLoginService {
    private final GoogleOAuthClient googleOAuthClient;
    private final OAuthAccountRepository accountRepository;
    private final AuthTokenIssuer tokenIssuer;

    @Override
    @Transactional
    public IssuedTokens login(String authorizationCode) {
        GoogleProfile profile = googleOAuthClient.exchangeAuthorizationCode(authorizationCode);
        if (!profile.emailVerified() || profile.subject().isBlank()) {
            throw new AuthException(AuthErrorCode.GOOGLE_ACCOUNT_NOT_VERIFIED);
        }
        OAuthAccount account = accountRepository.findByProviderAndProviderSubject("google", profile.subject())
                .orElseGet(() -> accountRepository.save(new OAuthAccount(UUID.randomUUID(), "google", profile.subject(),
                        profile.email(), Instant.now())));
        return tokenIssuer.issue(account.userId());
    }
}

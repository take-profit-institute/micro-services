package org.profit.candle.auth.identity.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.event.OutboxWriter;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.identity.entity.OAuthAccount;
import org.profit.candle.auth.identity.repository.OAuthAccountRepository;
import org.profit.candle.auth.oauth.OAuthClientRegistry;
import org.profit.candle.auth.oauth.OAuthProfile;
import org.profit.candle.auth.oauth.OAuthRedirectUriResolver;
import org.profit.candle.auth.token.service.AuthTokenIssuer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultOAuthLoginService implements OAuthLoginService {

    private final OAuthClientRegistry oAuthClientRegistry;
    private final OAuthRedirectUriResolver redirectUriResolver;
    private final OAuthAccountRepository accountRepository;
    private final AuthTokenIssuer tokenIssuer;
    private final OutboxWriter outboxWriter;

    @Override
    @Transactional
    public LoginResult login(String provider, String authorizationCode, String state, String redirectUri) {
        // 요청 redirect를 allowlist로 검증 → 인가/교환 redirect_uri 일치 보장(미지정이면 기본값=웹).
        String resolvedRedirectUri = redirectUriResolver.resolve(provider, redirectUri);
        OAuthProfile profile = oAuthClientRegistry.resolve(provider)
                .fetch(authorizationCode, state, resolvedRedirectUri);
        if (!profile.emailVerified() || profile.subject().isBlank()) {
            throw new AuthException(AuthErrorCode.OAUTH_ACCOUNT_NOT_VERIFIED);
        }
        var existing = accountRepository.findByProviderAndProviderSubject(provider, profile.subject());
        boolean isNewUser = existing.isEmpty();
        OAuthAccount account = existing.orElseGet(() -> {
            OAuthAccount created = accountRepository.save(new OAuthAccount(
                    UUID.randomUUID(), provider, profile.subject(), profile.email(), Instant.now()));
            outboxWriter.recordUserCreated(created.userId(), created.email());
            return created;
        });
        return new LoginResult(tokenIssuer.issue(account.userId(), account.email()), isNewUser);
    }
}

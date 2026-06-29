package org.profit.candle.auth.identity.service;

import org.profit.candle.auth.identity.entity.OAuthAccount;

public record AuthUserResult(String userId, String email, String provider, String providerSubject) {
    public static AuthUserResult from(OAuthAccount account) {
        return new AuthUserResult(
                account.userId().toString(),
                account.email(),
                account.provider(),
                account.providerSubject());
    }
}

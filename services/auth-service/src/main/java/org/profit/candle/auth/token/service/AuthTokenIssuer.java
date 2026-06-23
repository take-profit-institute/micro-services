package org.profit.candle.auth.token.service;

import java.util.UUID;

public interface AuthTokenIssuer {
    IssuedTokens issue(UUID userId, String email);
}

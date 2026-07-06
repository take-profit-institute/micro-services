package org.profit.candle.auth.token.service;

import java.util.UUID;
import org.profit.candle.auth.token.entity.PrincipalType;

public interface AuthTokenIssuer {
    IssuedTokens issue(UUID subject, String email, String role, PrincipalType type);
}

package org.profit.candle.auth.token.service;

public interface RefreshTokenService {
    IssuedTokens rotate(String refreshToken);
    void revoke(String refreshToken);
}

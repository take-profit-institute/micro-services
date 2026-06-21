package org.profit.candle.auth.token.service;

public record IssuedTokens(String accessToken, String refreshToken, long expiresInSeconds) {
}

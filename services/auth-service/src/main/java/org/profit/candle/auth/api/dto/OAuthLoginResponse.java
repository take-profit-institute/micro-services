package org.profit.candle.auth.api.dto;

public record OAuthLoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        boolean isNewUser) {
}

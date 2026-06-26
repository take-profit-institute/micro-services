package org.profit.candle.auth.oauth;

public record OAuthProfile(String subject, String email, boolean emailVerified) {
}

package org.profit.candle.auth.api.dto;

public record OAuthLoginRequest(String authorizationCode, String state, String redirectUri) {
}

package org.profit.candle.auth.identity.service;

public interface OAuthLoginService {
    LoginResult login(String provider, String authorizationCode, String state, String redirectUri);
}

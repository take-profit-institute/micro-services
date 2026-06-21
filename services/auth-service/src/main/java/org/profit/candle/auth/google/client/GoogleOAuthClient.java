package org.profit.candle.auth.google.client;

public interface GoogleOAuthClient {
    GoogleProfile exchangeAuthorizationCode(String authorizationCode);
}

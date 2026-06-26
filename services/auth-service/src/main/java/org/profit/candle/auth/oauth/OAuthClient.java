package org.profit.candle.auth.oauth;

public interface OAuthClient {
    String provider();
    OAuthProfile fetch(String authorizationCode);
}

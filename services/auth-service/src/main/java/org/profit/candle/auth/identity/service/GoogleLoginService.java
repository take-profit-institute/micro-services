package org.profit.candle.auth.identity.service;

public interface GoogleLoginService {
    LoginResult login(String authorizationCode);
}

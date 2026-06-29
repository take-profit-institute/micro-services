package org.profit.candle.auth.identity.service;

public interface AuthMeService {
    AuthUserResult getMe(String userId);
}

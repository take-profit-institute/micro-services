package org.profit.candle.auth.identity.service;

import org.profit.candle.auth.token.service.IssuedTokens;

public interface GoogleLoginService {
    IssuedTokens login(String authorizationCode);
}

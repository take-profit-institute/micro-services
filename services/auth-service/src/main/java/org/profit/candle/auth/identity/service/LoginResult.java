package org.profit.candle.auth.identity.service;

import org.profit.candle.auth.token.service.IssuedTokens;

public record LoginResult(IssuedTokens tokens, boolean isNewUser) {}

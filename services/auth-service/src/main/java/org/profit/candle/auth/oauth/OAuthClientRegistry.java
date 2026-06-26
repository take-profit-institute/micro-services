package org.profit.candle.auth.oauth;

import java.util.List;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.springframework.stereotype.Component;

@Component
public class OAuthClientRegistry {

    private final List<OAuthClient> clients;

    public OAuthClientRegistry(List<OAuthClient> clients) {
        this.clients = clients;
    }

    public OAuthClient resolve(String provider) {
        return clients.stream()
                .filter(c -> c.provider().equals(provider))
                .findFirst()
                .orElseThrow(() -> new AuthException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER));
    }
}

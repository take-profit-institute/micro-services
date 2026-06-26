package org.profit.candle.auth.identity.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.api.dto.ProvidersResponse;
import org.profit.candle.auth.config.AuthProperties;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthProvidersService {

    private final AuthProperties properties;

    public ProvidersResponse listProviders() {
        return ProvidersResponse.google(googleAuthorizationUrl());
    }

    public String googleAuthorizationUrl() {
        return "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id="
                + encode(properties.google().clientId()) + "&redirect_uri=" + encode(properties.google().redirectUri())
                + "&scope=" + encode("openid email profile") + "&access_type=offline&prompt=consent";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

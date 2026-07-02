package org.profit.candle.auth.api.dto;

import java.util.List;

public record ProvidersResponse(List<ProviderResponse> providers) {
    public static ProvidersResponse google(String authorizationUrl) {
        return new ProvidersResponse(List.of(new ProviderResponse("google", authorizationUrl)));
    }

    public static ProvidersResponse of(List<ProviderResponse> providers) {
        return new ProvidersResponse(List.copyOf(providers));
    }
}

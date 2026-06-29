package org.profit.candle.market.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomTokenRequest(
    @JsonProperty("grant_type")
    String grantType,

    @JsonProperty("appkey")
    String appKey,

    @JsonProperty("secretkey")
    String secretKey
) {
}

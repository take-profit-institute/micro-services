package org.profit.candle.market.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomOrderBookRequest(
        @JsonProperty("stk_cd")
        String stockCode
) {
}

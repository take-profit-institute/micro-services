package org.profit.candle.market.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomStockRequest(
        @JsonProperty("stk_cd")
        String stockCode
) {
}

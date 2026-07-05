package org.profit.candle.market.ranking.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomRateRankRequest(
        @JsonProperty("mrkt_tp")
        String marketType,

        @JsonProperty("sort_tp")
        String sortType,

        @JsonProperty("trde_qty_cnd")
        String tradingVolumeCondition,

        @JsonProperty("stk_cnd")
        String stockCondition,

        @JsonProperty("crd_cnd")
        String creditCondition,

        @JsonProperty("updown_incls")
        String upDownInclude,

        @JsonProperty("pric_cnd")
        String priceCondition,

        @JsonProperty("trde_prica_cnd")
        String tradingAmountCondition,

        @JsonProperty("stex_tp")
        String exchangeType
) {
    public static KiwoomRateRankRequest rateUp() {
        return new KiwoomRateRankRequest(
                "000", "1", "0000", "0", "0", "1", "0", "0", "3"
        );
    }

    public static KiwoomRateRankRequest rateDown() {
        return new KiwoomRateRankRequest(
                "000", "3", "0000", "0", "0", "1", "0", "0", "3"
        );
    }
}

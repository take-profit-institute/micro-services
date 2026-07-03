package org.profit.candle.market.ranking.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomPriceRankRequest (
    @JsonProperty("mrkt_tp")
    String marketType,

    @JsonProperty("flu_tp")
    String fluctuationType,

    @JsonProperty("tm_tp")
    String timeType,

    @JsonProperty("tm")
    String time,

    @JsonProperty("trde_qty_tp")
    String tradingVolumeType,

    @JsonProperty("stk_cnd")
    String stockCondition,

    @JsonProperty("crd_cnd")
    String creditCondition,

    @JsonProperty("pric_cnd")
    String priceCondition,

    @JsonProperty("updown_incls")
    String updownIncludes,

    @JsonProperty("stex_tp")
    String exchangeType
) {
    public static KiwoomPriceRankRequest rising() {
        return new KiwoomPriceRankRequest(
                "000",
                "1",
                "1",
                "10",
                "00000",
                "0",
                "0",
                "0",
                "1",
                "1"
        );
    }

    public static KiwoomPriceRankRequest falling() {
        return new KiwoomPriceRankRequest(
                "000",
                "2",
                "1",
                "10",
                "00000",
                "0",
                "0",
                "0",
                "1",
                "1"
        );
    }
}

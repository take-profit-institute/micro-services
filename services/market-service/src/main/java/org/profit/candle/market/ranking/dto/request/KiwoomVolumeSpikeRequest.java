package org.profit.candle.market.ranking.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomVolumeSpikeRequest(
        @JsonProperty("mrkt_tp")
        String marketType,

        @JsonProperty("sort_tp")
        String sortType,

        @JsonProperty("tm_tp")
        String timeType,

        @JsonProperty("trde_qty_tp")
        String tradingVolumeType,

        @JsonProperty("tm")
        String time,

        @JsonProperty("stk_cnd")
        String stockCondition,

        @JsonProperty("pric_tp")
        String priceType,

        @JsonProperty("stex_tp")
        String exchangeType
) {
    public static KiwoomVolumeSpikeRequest volumeSpike() {
        return new KiwoomVolumeSpikeRequest(
                "000",
                "1",
                "2",
                "5",
                "",
                "0",
                "0",
                "3"
        );
    }
}

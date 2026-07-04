package org.profit.candle.market.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomVolumeSpikeItem(
        @JsonProperty("stk_cd")
        String stockCode,

        @JsonProperty("stk_nm")
        String stockName,

        @JsonProperty("cur_prc")
        String currentPrice,

        @JsonProperty("pred_pre_sig")
        String priceChangeSign,

        @JsonProperty("pred_pre")
        String priceChange,

        @JsonProperty("flu_rt")
        String priceChangeRate,

        @JsonProperty("prev_trde_qty")
        String previousTradingVolume,

        @JsonProperty("now_trde_qty")
        String tradingVolume,

        @JsonProperty("sdnin_qty")
        String volumeSpikeAmount,

        @JsonProperty("sdnin_rt")
        String volumeSpikeRate
) {
}

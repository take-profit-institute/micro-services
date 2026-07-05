package org.profit.candle.market.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomRateRankItem(
        @JsonProperty("stk_cls")
        String stockClass,

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

        @JsonProperty("sel_req")
        String sellRemainingQuantity,

        @JsonProperty("buy_req")
        String buyRemainingQuantity,

        @JsonProperty("now_trde_qty")
        String tradingVolume,

        @JsonProperty("cntr_str")
        String tradingStrength,

        @JsonProperty("cnt")
        String count
) {
}

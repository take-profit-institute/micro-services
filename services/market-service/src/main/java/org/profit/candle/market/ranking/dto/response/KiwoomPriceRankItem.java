package org.profit.candle.market.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomPriceRankItem(
        @JsonProperty("stk_cd")
        String stockCode,

        @JsonProperty("stk_nm")
        String stockName,

        @JsonProperty("cur_prc")
        String currentPrice,

        @JsonProperty("base_pre")
        String priceChange,

        @JsonProperty("flu_rt")
        String priceChangeRate,

        @JsonProperty("trde_qty")
        String tradingVolume,

        @JsonProperty("pred_pre_sig")
        String priceChangeSign
){
}

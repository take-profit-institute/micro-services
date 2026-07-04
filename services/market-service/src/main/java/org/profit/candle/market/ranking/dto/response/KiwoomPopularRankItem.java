package org.profit.candle.market.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomPopularRankItem(
        @JsonProperty("stk_cd")
        String stockCode,

        @JsonProperty("stk_nm")
        String stockName,

        @JsonProperty("bigd_rank")
        String rank,

        @JsonProperty("rank_chg")
        String rankChange,

        @JsonProperty("rank_chg_sign")
        String rankChangeSign,

        @JsonProperty("past_curr_prc")
        String currentPrice,

        @JsonProperty("base_comp_sign")
        String priceChangeSign,

        @JsonProperty("base_comp_chgr")
        String priceChangeRate,

        @JsonProperty("dt")
        String date,

        @JsonProperty("tm")
        String time
) {
}

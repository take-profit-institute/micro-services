package org.profit.candle.market.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KiwoomRateRankResponse(
        @JsonProperty("pred_pre_flu_rt_upper")
        List<KiwoomRateRankItem> items,

        @JsonProperty("return_code")
        int returnCode,

        @JsonProperty("return_msg")
        String returnMsg
) {
}
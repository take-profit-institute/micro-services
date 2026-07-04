package org.profit.candle.market.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KiwoomPriceRankResponse(
        @JsonProperty("pric_jmpflu")
        List<KiwoomPriceRankItem> items,

        @JsonProperty("return_code")
        int returnCode,

        @JsonProperty("return_msg")
        String returnMsg
) {
}

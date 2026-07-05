package org.profit.candle.market.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KiwoomPopularRankResponse(
        @JsonProperty("item_inq_rank")
        List<KiwoomPopularRankItem> items,

        @JsonProperty("return_code")
        int returnCode,

        @JsonProperty("return_msg")
        String returnMsg
) {
}

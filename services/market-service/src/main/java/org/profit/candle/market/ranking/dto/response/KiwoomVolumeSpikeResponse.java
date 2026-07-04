package org.profit.candle.market.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record KiwoomVolumeSpikeResponse(
        @JsonProperty("trde_qty_sdnin")
        List<KiwoomVolumeSpikeItem> items,

        @JsonProperty("return_code")
        int returnCode,

        @JsonProperty("return_msg")
        String returnMsg
) {
}

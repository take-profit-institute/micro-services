package org.profit.candle.market.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomTokenResponse(
        @JsonProperty("expires_dt")
        String expiresDt,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("token")
        String token,

        @JsonProperty("return_code")
        int returnCode,

        @JsonProperty("return_msg")
        String returnMsg
){
}

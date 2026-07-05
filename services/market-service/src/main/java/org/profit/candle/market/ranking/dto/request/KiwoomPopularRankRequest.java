package org.profit.candle.market.ranking.dto.request;


import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomPopularRankRequest(
        @JsonProperty("qry_tp")
        String queryType

) {
    public static KiwoomPopularRankRequest popular() {
        return new KiwoomPopularRankRequest("1");
    }
}
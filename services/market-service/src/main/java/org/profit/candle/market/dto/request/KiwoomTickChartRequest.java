package org.profit.candle.market.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 주식틱차트조회(ka10079) 요청. tic_scope=1(1틱), upd_stkpc_tp=1(수정주가 반영). */
public record KiwoomTickChartRequest(
        @JsonProperty("stk_cd") String stockCode,
        @JsonProperty("tic_scope") String ticScope,
        @JsonProperty("upd_stkpc_tp") String updStkpcType
) {
    public static KiwoomTickChartRequest ofOneTick(String stockCode) {
        return new KiwoomTickChartRequest(stockCode, "1", "1");
    }
}

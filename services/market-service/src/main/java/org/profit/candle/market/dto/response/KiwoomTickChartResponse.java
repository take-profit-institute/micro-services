package org.profit.candle.market.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 주식틱차트조회(ka10079) 응답. */
public record KiwoomTickChartResponse(
        @JsonProperty("stk_cd") String stockCode,
        @JsonProperty("last_tic_cnt") String lastTicCnt,
        @JsonProperty("stk_tic_chart_qry") List<Item> ticks,
        @JsonProperty("return_code") int returnCode,
        @JsonProperty("return_msg") String returnMsg
) {
    /** 틱 1건. cur_prc/시각만 그래프에 쓰고 나머지는 참고용. */
    public record Item(
            @JsonProperty("cur_prc") String currentPrice,
            @JsonProperty("trde_qty") String tradingVolume,
            @JsonProperty("cntr_tm") String contractTime,   // YYYYMMDDHHmmss
            @JsonProperty("open_pric") String openPrice,
            @JsonProperty("high_pric") String highPrice,
            @JsonProperty("low_pric") String lowPrice,
            @JsonProperty("pred_pre") String priceChange,
            @JsonProperty("pred_pre_sig") String priceChangeSign
    ) {
    }
}

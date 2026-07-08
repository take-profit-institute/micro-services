package org.profit.candle.market.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomOrderBookResponse(
        @JsonProperty("bid_req_base_tm")
        String baseTime,
        @JsonProperty("sel_fpr_bid")
        String bestAskPrice,
        @JsonProperty("sel_fpr_req")
        String bestAskQuantity,
        @JsonProperty("buy_fpr_bid")
        String bestBidPrice,
        @JsonProperty("buy_fpr_req")
        String bestBidQuantity,
        @JsonProperty("sel_2th_pre_bid")
        String ask2Price,
        @JsonProperty("sel_2th_pre_req")
        String ask2Quantity,
        @JsonProperty("sel_3th_pre_bid")
        String ask3Price,
        @JsonProperty("sel_3th_pre_req")
        String ask3Quantity,
        @JsonProperty("sel_4th_pre_bid")
        String ask4Price,
        @JsonProperty("sel_4th_pre_req")
        String ask4Quantity,
        @JsonProperty("sel_5th_pre_bid")
        String ask5Price,
        @JsonProperty("sel_5th_pre_req")
        String ask5Quantity,
        @JsonProperty("sel_6th_pre_bid")
        String ask6Price,
        @JsonProperty("sel_6th_pre_req")
        String ask6Quantity,
        @JsonProperty("sel_7th_pre_bid")
        String ask7Price,
        @JsonProperty("sel_7th_pre_req")
        String ask7Quantity,
        @JsonProperty("sel_8th_pre_bid")
        String ask8Price,
        @JsonProperty("sel_8th_pre_req")
        String ask8Quantity,
        @JsonProperty("sel_9th_pre_bid")
        String ask9Price,
        @JsonProperty("sel_9th_pre_req")
        String ask9Quantity,
        @JsonProperty("sel_10th_pre_bid")
        String ask10Price,
        @JsonProperty("sel_10th_pre_req")
        String ask10Quantity,
        @JsonProperty("buy_2th_pre_bid")
        String bid2Price,
        @JsonProperty("buy_2th_pre_req")
        String bid2Quantity,
        @JsonProperty("buy_3th_pre_bid")
        String bid3Price,
        @JsonProperty("buy_3th_pre_req")
        String bid3Quantity,
        @JsonProperty("buy_4th_pre_bid")
        String bid4Price,
        @JsonProperty("buy_4th_pre_req")
        String bid4Quantity,
        @JsonProperty("buy_5th_pre_bid")
        String bid5Price,
        @JsonProperty("buy_5th_pre_req")
        String bid5Quantity,
        @JsonProperty("buy_6th_pre_bid")
        String bid6Price,
        @JsonProperty("buy_6th_pre_req")
        String bid6Quantity,
        @JsonProperty("buy_7th_pre_bid")
        String bid7Price,
        @JsonProperty("buy_7th_pre_req")
        String bid7Quantity,
        @JsonProperty("buy_8th_pre_bid")
        String bid8Price,
        @JsonProperty("buy_8th_pre_req")
        String bid8Quantity,
        @JsonProperty("buy_9th_pre_bid")
        String bid9Price,
        @JsonProperty("buy_9th_pre_req")
        String bid9Quantity,
        @JsonProperty("buy_10th_pre_bid")
        String bid10Price,
        @JsonProperty("buy_10th_pre_req")
        String bid10Quantity,
        @JsonProperty("tot_sel_req")
        String totalAskQuantity,
        @JsonProperty("tot_buy_req")
        String totalBidQuantity,
        @JsonProperty("return_code")
        Integer returnCode,
        @JsonProperty("return_msg")
        String returnMsg
) {
}

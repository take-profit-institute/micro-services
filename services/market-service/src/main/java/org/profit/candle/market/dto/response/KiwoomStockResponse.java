package org.profit.candle.market.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import static java.lang.Integer.parseInt;

public record KiwoomStockResponse(
    @JsonProperty("stk_cd")
    String stockCode,

    @JsonProperty("stk_nm")
    String stockName,

    @JsonProperty("cur_prc")
    String currentPrice,

    @JsonProperty("flu_rt")
    String priceChangeRate,

    @JsonProperty("pre_sig")
    String priceChangeSign,

    @JsonProperty("trde_qty")
    String tradingVolume,

    @JsonProperty("return_code")
    int returnCode,

    @JsonProperty("return_msg")
    String returnMsg,

    @JsonProperty("pred_pre")
    String priceChange
) {

    public int getCurrentPriceValue() {
        return Math.abs(parseInt(currentPrice));
    }

    public double getPriceChangeRateValue() {
        return parseDouble(priceChangeRate);
    }

    public long getTradingVolumeValue() {
        return parseLong(tradingVolume);
    }

    public int getPriceChangeSignValue() {
        return parseInt(priceChangeSign);
    }

    public int getPriceChangeValue() {
        return parseInt(priceChange);
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value.replace("+", "").replace(",", "").trim());
    }

    private static long parseLong(String value) {
        return Long.parseLong(value.replace("+", "").replace(",", "").trim());
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value.replace("+", "").replace("%", "").replace(",", "").trim());
    }
}

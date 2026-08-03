package org.profit.candle.batch.stock.candle.model;

/**
 * 한 종목의 일봉 백필 결과. 실패한 종목도 예외 대신 이 값으로 흘려보내
 * faultTolerant 청크 롤백/스캔(= 청크 전체의 키움 재호출)을 피한다.
 */
public record CandleIngestResult(String code, int upserted, String failureReason) {

    public static CandleIngestResult succeeded(String code, int upserted) {
        return new CandleIngestResult(code, upserted, null);
    }

    public static CandleIngestResult failed(String code, String failureReason) {
        return new CandleIngestResult(code, 0, failureReason);
    }

    public boolean failed() {
        return failureReason != null;
    }
}

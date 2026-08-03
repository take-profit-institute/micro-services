package org.profit.candle.batch.stock.candle.exception;

import org.profit.candle.common.error.CandleException;

/**
 * 종목 단위 백필 실패가 누적 상한을 넘었을 때(= 대량 장애) 잡을 즉시 FAILED 로 떨군다.
 * skip 대상인 {@link StockCandleException} 과 별도 타입이어야 writer 에서 던져도 청크 스캔을 타지 않는다.
 */
public class CandleIngestFailureLimitExceededException extends CandleException {

    private final int failures;
    private final int limit;

    public CandleIngestFailureLimitExceededException(int failures, int limit) {
        super(StockCandleErrorCode.FAILURE_LIMIT_EXCEEDED);
        this.failures = failures;
        this.limit = limit;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " (failures=" + failures + ", limit=" + limit + ")";
    }

    public int failures() {
        return failures;
    }

    public int limit() {
        return limit;
    }
}

package org.profit.candle.batch.ranking.exception;

import org.profit.candle.common.error.ErrorCode;

public enum RankingBatchErrorCode implements ErrorCode {
    PORTFOLIO_EOD_NOT_COMPLETED(
            "BATCH_RANKING_PORTFOLIO_EOD_NOT_COMPLETED",
            "동일 거래일의 Portfolio EOD 배치가 완료되지 않았습니다.",
            false
    ),
    EXTERNAL_CLIENT_FAILED(
            "BATCH_RANKING_EXTERNAL_CLIENT_FAILED",
            "Ranking Service 호출에 실패했습니다.",
            false
    ),
    EXTERNAL_CLIENT_RETRYABLE(
            "BATCH_RANKING_EXTERNAL_CLIENT_RETRYABLE",
            "재시도 가능한 Ranking Service 오류가 발생했습니다.",
            true
    ),
    RESPONSE_INVALID(
            "BATCH_RANKING_RESPONSE_INVALID",
            "Ranking Service 응답이 올바르지 않습니다.",
            false
    );

    private final String code;
    private final String message;
    private final boolean retryable;

    RankingBatchErrorCode(String code, String message, boolean retryable) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    public boolean retryable() {
        return retryable;
    }
}

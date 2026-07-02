package org.profit.candle.ranking.ranking.exception;

import org.profit.candle.common.error.ErrorCode;

public enum RankingErrorCode implements ErrorCode {
    PORTFOLIO_SNAPSHOT_SERVICE_UNAVAILABLE(
            "RANKING_PORTFOLIO_SNAPSHOT_SERVICE_UNAVAILABLE",
            "포트폴리오 스냅샷 서비스를 사용할 수 없습니다."),
    INVALID_PORTFOLIO_SNAPSHOT(
            "RANKING_INVALID_PORTFOLIO_SNAPSHOT",
            "포트폴리오 스냅샷 데이터가 올바르지 않습니다.");

    private final String code;
    private final String message;

    RankingErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 외부에 노출할 안정적인 오류 코드를 반환한다. */
    @Override
    public String code() {
        return code;
    }

    /** 외부에 노출할 안전한 오류 메시지를 반환한다. */
    @Override
    public String message() {
        return message;
    }
}

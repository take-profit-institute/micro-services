package org.profit.candle.ranking.ranking.exception;

import org.profit.candle.common.error.CandleException;

public class RankingException extends CandleException {

    /** Ranking 도메인 오류 코드로 예외를 생성한다. */
    public RankingException(RankingErrorCode errorCode) {
        super(errorCode);
    }

    /** 외부 서비스나 저장소의 원인을 보존하면서 Ranking 오류로 변환한다. */
    public RankingException(RankingErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}

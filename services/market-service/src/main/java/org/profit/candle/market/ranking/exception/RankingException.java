package org.profit.candle.market.ranking.exception;

import lombok.Getter;

@Getter
public class RankingException extends RuntimeException {

    private final RankingErrorCode errorCode;

    public RankingException(RankingErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
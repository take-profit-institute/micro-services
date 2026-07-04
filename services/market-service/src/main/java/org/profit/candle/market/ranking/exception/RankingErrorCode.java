package org.profit.candle.market.ranking.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RankingErrorCode {

    RANKING_API_ERROR(
            "R001",
            "키움 랭킹 API 호출에 실패했습니다."
    ),

    EMPTY_RANKING_DATA(
            "R002",
            "랭킹 데이터가 존재하지 않습니다."
    );

    private final String code;
    private final String message;
}
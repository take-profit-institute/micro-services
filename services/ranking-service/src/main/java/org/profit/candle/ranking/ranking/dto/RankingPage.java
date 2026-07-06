package org.profit.candle.ranking.ranking.dto;

import java.util.List;

public record RankingPage(List<RankingResult> rankings, String nextPageToken) {

    /** 조회 결과가 외부에서 변경되지 않도록 복사한다. */
    public RankingPage {
        rankings = List.copyOf(rankings);
    }
}

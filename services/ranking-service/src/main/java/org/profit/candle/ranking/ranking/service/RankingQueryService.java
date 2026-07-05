package org.profit.candle.ranking.ranking.service;

import java.util.UUID;
import org.profit.candle.ranking.ranking.dto.RankingPage;
import org.profit.candle.ranking.ranking.dto.RankingResult;

public interface RankingQueryService {

    /** 마지막 완료 랭킹의 TOP 100 범위에서 cursor 페이지를 조회한다. */
    RankingPage listRankings(int pageSize, String pageToken);

    /** 마지막 완료일 기준 사용자의 순위를 조회한다. */
    RankingResult getMyRanking(UUID userId);
}

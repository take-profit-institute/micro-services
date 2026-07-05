package org.profit.candle.batch.ranking.client;

import java.time.LocalDate;

/** Ranking Service의 일별 랭킹 확정 명령 계약이다. */
public interface RankingBatchClient {

    Result finalizeDailyRanking(LocalDate rankingDate, String idempotencyKey);

    record Result(
            LocalDate rankingDate,
            int rankedUserCount
    ) {
    }
}

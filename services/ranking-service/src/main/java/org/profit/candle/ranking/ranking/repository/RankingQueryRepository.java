package org.profit.candle.ranking.ranking.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.ranking.ranking.dto.RankingResult;

public interface RankingQueryRepository {

    /** 가장 최근 완료된 랭킹 날짜를 조회한다. */
    Optional<LocalDate> findLatestCompletedDate();

    /** 특정 날짜에서 지정 순위 다음 결과를 최대 limit건 조회한다. */
    List<RankingResult> findRankings(LocalDate rankingDate, int afterPosition, int limit);

    /** 특정 날짜의 사용자 순위를 조회한다. */
    Optional<RankingResult> findUserRanking(LocalDate rankingDate, UUID userId);
}

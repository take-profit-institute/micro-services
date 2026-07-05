package org.profit.candle.ranking.ranking.cache;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.ranking.ranking.dto.RankingResult;

public interface RankingCache {

    /** Redis에 저장된 마지막 완료일을 조회한다. */
    Optional<LocalDate> findLatestDate();

    /** 마지막 완료일을 Redis에 저장한다. */
    void putLatestDate(LocalDate rankingDate);

    /** 특정 날짜의 TOP 100 캐시를 조회한다. */
    Optional<List<RankingResult>> findTopRankings(LocalDate rankingDate);

    /** 특정 날짜의 TOP 100을 Redis에 저장한다. */
    void putTopRankings(LocalDate rankingDate, List<RankingResult> rankings);

    /** 특정 사용자의 순위 캐시를 조회한다. */
    Optional<RankingResult> findUserRanking(LocalDate rankingDate, UUID userId);

    /** 특정 사용자의 순위를 Redis에 저장한다. */
    void putUserRanking(RankingResult ranking);
}

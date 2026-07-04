package org.profit.candle.ranking.ranking.repository;

import java.time.LocalDate;
import java.util.List;
import org.profit.candle.ranking.ranking.dto.DailyRankingRow;
import org.profit.candle.ranking.ranking.dto.RankingParticipantCandidate;

public interface DailyRankingRepository {

    /** 현재까지 투영된 모든 랭킹 참가자 정보를 조회한다. */
    List<RankingParticipantCandidate> findParticipants();

    /** 특정 날짜의 계산 입력·순위·완료 기록을 하나의 트랜잭션으로 교체한다. */
    void replaceDailyRanking(LocalDate rankingDate, List<DailyRankingRow> rankings);
}

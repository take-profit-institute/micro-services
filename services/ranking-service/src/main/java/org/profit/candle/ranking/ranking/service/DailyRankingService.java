package org.profit.candle.ranking.ranking.service;

import java.time.LocalDate;
import org.profit.candle.ranking.ranking.dto.DailyRankingResult;

public interface DailyRankingService {

    /** Portfolio EOD 결과로 특정 거래일의 랭킹을 계산하고 저장한다. */
    DailyRankingResult finalizeDailyRanking(LocalDate rankingDate);
}

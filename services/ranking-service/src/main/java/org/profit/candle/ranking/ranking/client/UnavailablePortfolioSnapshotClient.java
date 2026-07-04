package org.profit.candle.ranking.ranking.client;

import java.time.LocalDate;
import org.profit.candle.ranking.ranking.exception.RankingErrorCode;
import org.profit.candle.ranking.ranking.exception.RankingException;
import org.springframework.stereotype.Component;

@Component
public class UnavailablePortfolioSnapshotClient implements PortfolioSnapshotClient {

    /** #105가 연결되기 전 운영 환경에서 가짜 데이터를 만들지 않고 명확히 실패한다. */
    @Override
    public PortfolioSnapshotPage listDailySnapshots(LocalDate snapshotDate, String pageToken, int pageSize) {
        throw new RankingException(RankingErrorCode.PORTFOLIO_SNAPSHOT_SERVICE_UNAVAILABLE);
    }
}

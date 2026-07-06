package org.profit.candle.ranking.ranking.client;

import java.time.LocalDate;

public interface PortfolioSnapshotClient {

    /** #105의 일별 Portfolio 스냅샷을 user_id 오름차순으로 한 페이지 조회한다. */
    PortfolioSnapshotPage listDailySnapshots(LocalDate snapshotDate, String pageToken, int pageSize);
}

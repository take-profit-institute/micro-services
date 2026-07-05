package org.profit.candle.batch.portfolio.eod.client;

import java.time.LocalDate;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;

/** Portfolio의 활성 보유자 cursor 조회 계약이다. */
public interface SnapshotTargetClient {

    SnapshotTarget.Page loadTargets(LocalDate businessDate, String pageToken, int pageSize);
}

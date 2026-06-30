package org.profit.candle.batch.portfolio.eod.client;

import java.time.LocalDate;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;

/** PortfolioService에 cursor 조회 RPC가 추가되면 gRPC adapter를 연결한다. */
public interface SnapshotTargetClient {

    SnapshotTarget.Page loadTargets(LocalDate businessDate, String pageToken, int pageSize);
}

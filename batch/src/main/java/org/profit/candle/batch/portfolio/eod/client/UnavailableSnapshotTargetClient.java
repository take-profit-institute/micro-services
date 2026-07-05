package org.profit.candle.batch.portfolio.eod.client;

import java.time.LocalDate;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Portfolio가 전체 활성 보유자를 조회하는 계약을 제공할 때 gRPC adapter로 교체한다. */
@Component
@Profile("!local-eod")
public class UnavailableSnapshotTargetClient implements SnapshotTargetClient {

    @Override
    public SnapshotTarget.Page loadTargets(
            LocalDate businessDate,
            String pageToken,
            int pageSize
    ) {
        throw new EodBatchException(
                EodBatchErrorCode.SNAPSHOT_TARGET_CONTRACT_UNAVAILABLE
        );
    }
}

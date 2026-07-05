package org.profit.candle.batch.portfolio.eod.client;

import java.time.LocalDate;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Trading이 원금 조회 계약을 제공할 때 gRPC adapter로 교체한다. */
@Component
@Profile("!local-eod")
public class UnavailableSeedCapitalProvider implements SeedCapitalProvider {

    @Override
    public long getSeedCapital(String userId, LocalDate businessDate) {
        throw new EodBatchException(
                EodBatchErrorCode.SEED_CAPITAL_CONTRACT_UNAVAILABLE
        );
    }
}

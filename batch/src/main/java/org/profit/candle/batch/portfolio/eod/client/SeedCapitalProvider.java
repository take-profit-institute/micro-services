package org.profit.candle.batch.portfolio.eod.client;

import java.time.LocalDate;

/** 원금 정책이 확정되면 소유 서비스 adapter를 연결한다. 운영용 기본값은 두지 않는다. */
public interface SeedCapitalProvider {

    long getSeedCapital(String userId, LocalDate businessDate);
}

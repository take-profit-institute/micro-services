package org.profit.candle.batch.portfolio.eod.client;

import java.time.LocalDate;

/** 누적 수익률 계산에 사용할 사용자 원금 정책이다. */
public interface SeedCapitalProvider {

    long getSeedCapital(String userId, LocalDate businessDate);
}

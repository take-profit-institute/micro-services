package org.profit.candle.batch.portfolio.eod.client;

import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 입출금 기능 도입 전 모든 Trading 계좌에 지급되는 초기 원금 1억 원 정책이다. */
@Component
@Profile("!local-eod")
public class FixedSeedCapitalProvider implements SeedCapitalProvider {

    public static final long INITIAL_SEED_CAPITAL_KRW = 100_000_000L;

    /** 현재 모든 사용자와 거래일에 동일한 초기 원금을 반환한다. */
    @Override
    public long getSeedCapital(String userId, LocalDate businessDate) {
        return INITIAL_SEED_CAPITAL_KRW;
    }
}

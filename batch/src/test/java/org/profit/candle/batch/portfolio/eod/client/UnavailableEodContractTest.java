package org.profit.candle.batch.portfolio.eod.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;

class UnavailableEodContractTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 5);

    @Test
    void seedCapitalProviderFailsWhenTradingContractIsUnavailable() {
        SeedCapitalProvider provider = new UnavailableSeedCapitalProvider();

        assertThatThrownBy(() -> provider.getSeedCapital("user-id", BUSINESS_DATE))
                .isInstanceOf(EodBatchException.class)
                .satisfies(exception -> assertErrorCode(
                        exception,
                        EodBatchErrorCode.SEED_CAPITAL_CONTRACT_UNAVAILABLE
                ));
    }

    private void assertErrorCode(Throwable throwable, EodBatchErrorCode expected) {
        EodBatchException exception = (EodBatchException) throwable;
        org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo(expected);
    }
}

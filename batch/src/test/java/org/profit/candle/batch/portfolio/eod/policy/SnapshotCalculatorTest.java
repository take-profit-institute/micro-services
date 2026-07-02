package org.profit.candle.batch.portfolio.eod.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;

class SnapshotCalculatorTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 6, 29);

    private final SnapshotCalculator calculator = new SnapshotCalculator();

    @Test
    void shouldCalculateStockValueAndTotalAsset() {
        SnapshotTarget target = new SnapshotTarget(
                "user-1",
                List.of(
                        new SnapshotTarget.Holding("005930", 2, 60_000),
                        new SnapshotTarget.Holding("000660", 3, 100_000)
                )
        );
        Map<String, ClosingPrice> prices = Map.of(
                "005930", price("005930", 70_000),
                "000660", price("000660", 110_000)
        );

        SnapshotCommand.CalculationContext context = new SnapshotCommand.CalculationContext(
                BUSINESS_DATE,
                100_000,
                500_000,
                prices,
                "key"
        );
        SnapshotCommand result = calculator.calculate(target, context);

        assertThat(result.stockValue()).isEqualTo(470_000);
        assertThat(result.totalAsset()).isEqualTo(570_000);
        assertThat(result.seedCapital()).isEqualTo(500_000);
    }

    @Test
    void shouldRejectMissingPriceInsteadOfUsingFallback() {
        SnapshotTarget target = new SnapshotTarget(
                "user-1",
                List.of(new SnapshotTarget.Holding("005930", 1, 60_000))
        );

        SnapshotCommand.CalculationContext context = new SnapshotCommand.CalculationContext(
                BUSINESS_DATE,
                100_000,
                100_000,
                Map.of(),
                "key"
        );

        assertThatThrownBy(() -> calculator.calculate(target, context))
                .isInstanceOf(EodBatchException.class)
                .satisfies(exception -> assertThat(
                        ((EodBatchException) exception).errorCode()
                ).isEqualTo(EodBatchErrorCode.CLOSING_PRICE_INVALID));
    }

    @Test
    void shouldRejectUnknownSeedCapitalPolicy() {
        SnapshotTarget target = new SnapshotTarget("user-1", List.of());

        SnapshotCommand.CalculationContext context = new SnapshotCommand.CalculationContext(
                BUSINESS_DATE,
                100_000,
                0,
                Map.of(),
                "key"
        );

        assertThatThrownBy(() -> calculator.calculate(target, context))
                .isInstanceOf(EodBatchException.class)
                .satisfies(exception -> assertThat(
                        ((EodBatchException) exception).errorCode()
                ).isEqualTo(EodBatchErrorCode.SEED_CAPITAL_INVALID));
    }

    @Test
    void shouldFailOnAssetOverflow() {
        SnapshotTarget target = new SnapshotTarget(
                "user-1",
                List.of(new SnapshotTarget.Holding("005930", Long.MAX_VALUE, 1))
        );

        SnapshotCommand.CalculationContext context = new SnapshotCommand.CalculationContext(
                BUSINESS_DATE,
                1,
                100,
                Map.of("005930", price("005930", 2)),
                "key"
        );

        assertThatThrownBy(() -> calculator.calculate(target, context))
                .isInstanceOf(ArithmeticException.class);
    }

    private ClosingPrice price(String symbol, long price) {
        return new ClosingPrice(symbol, price, Instant.EPOCH);
    }
}

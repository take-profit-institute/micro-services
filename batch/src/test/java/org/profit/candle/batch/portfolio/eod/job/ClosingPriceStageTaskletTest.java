package org.profit.candle.batch.portfolio.eod.job;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.client.ClosingPriceClient;
import org.profit.candle.batch.portfolio.eod.client.SnapshotTargetClient;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.profit.candle.batch.portfolio.eod.policy.EodRetryExecutor;
import org.profit.candle.batch.portfolio.eod.repository.ClosingPriceStageRepository;

class ClosingPriceStageTaskletTest {

    @Test
    void shouldStagePricesFromFakeMarketClient() throws Exception {
        LocalDate businessDate = LocalDate.of(2026, 6, 29);
        SnapshotTargetClient targetClient = new SnapshotTargetClient() {
            @Override
            public SnapshotTarget.Page loadTargets(
                    LocalDate date,
                    String pageToken,
                    int pageSize
            ) {
                return new SnapshotTarget.Page(
                        List.of(
                                new SnapshotTarget(
                                        "user-1",
                                        List.of(
                                                new SnapshotTarget.Holding("005930", 1, 60_000),
                                                new SnapshotTarget.Holding("000660", 1, 100_000)
                                        )
                                ),
                                new SnapshotTarget(
                                        "user-2",
                                        List.of(new SnapshotTarget.Holding("005930", 2, 65_000))
                                )
                        ),
                        ""
                );
            }
        };
        Map<String, Long> fakePrices = Map.of("005930", 70_000L, "000660", 110_000L);
        ClosingPriceClient marketClient = (date, symbols) -> symbols.stream()
                .map(symbol -> new ClosingPrice(
                        symbol,
                        fakePrices.get(symbol),
                        date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
                ))
                .toList();
        CapturingPriceRepository repository = new CapturingPriceRepository();
        ClosingPriceStageTasklet tasklet = new ClosingPriceStageTasklet(
                11L,
                businessDate,
                500,
                targetClient,
                marketClient,
                repository,
                new EodRetryExecutor()
        );

        tasklet.execute(null, null);

        org.assertj.core.api.Assertions.assertThat(repository.jobInstanceId).isEqualTo(11L);
        org.assertj.core.api.Assertions.assertThat(repository.businessDate)
                .isEqualTo(businessDate);
        org.assertj.core.api.Assertions.assertThat(repository.prices)
                .extracting(ClosingPrice::price)
                .containsExactly(70_000L, 110_000L);
    }

    private static final class CapturingPriceRepository extends ClosingPriceStageRepository {

        private long jobInstanceId;
        private LocalDate businessDate;
        private List<ClosingPrice> prices;

        private CapturingPriceRepository() {
            super(null);
        }

        @Override
        public void upsertAll(
                long jobInstanceId,
                LocalDate businessDate,
                List<ClosingPrice> prices
        ) {
            this.jobInstanceId = jobInstanceId;
            this.businessDate = businessDate;
            this.prices = List.copyOf(prices);
        }
    }
}

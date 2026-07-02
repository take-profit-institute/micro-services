package org.profit.candle.batch.portfolio.eod.processor;

import java.time.LocalDate;
import java.util.Map;
import org.profit.candle.batch.portfolio.eod.client.CashBalanceClient;
import org.profit.candle.batch.portfolio.eod.client.SeedCapitalProvider;
import org.profit.candle.batch.portfolio.eod.idempotency.SnapshotIdempotencyKeyFactory;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.profit.candle.batch.portfolio.eod.policy.EodRetryExecutor;
import org.profit.candle.batch.portfolio.eod.policy.SnapshotCalculator;
import org.profit.candle.batch.portfolio.eod.repository.ClosingPriceStageRepository;
import org.springframework.batch.infrastructure.item.ItemProcessor;

public class SnapshotItemProcessor implements ItemProcessor<SnapshotTarget, SnapshotCommand> {

    private final long jobInstanceId;
    private final LocalDate businessDate;
    private final CashBalanceClient cashBalanceClient;
    private final SeedCapitalProvider seedCapitalProvider;
    private final ClosingPriceStageRepository priceRepository;
    private final SnapshotCalculator calculator;
    private final SnapshotIdempotencyKeyFactory keyFactory;
    private final EodRetryExecutor retryExecutor;

    private Map<String, ClosingPrice> prices;

    public SnapshotItemProcessor(
            long jobInstanceId,
            LocalDate businessDate,
            CashBalanceClient cashBalanceClient,
            SeedCapitalProvider seedCapitalProvider,
            ClosingPriceStageRepository priceRepository,
            SnapshotCalculator calculator,
            SnapshotIdempotencyKeyFactory keyFactory,
            EodRetryExecutor retryExecutor
    ) {
        this.jobInstanceId = jobInstanceId;
        this.businessDate = businessDate;
        this.cashBalanceClient = cashBalanceClient;
        this.seedCapitalProvider = seedCapitalProvider;
        this.priceRepository = priceRepository;
        this.calculator = calculator;
        this.keyFactory = keyFactory;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public SnapshotCommand process(SnapshotTarget target) {
        if (prices == null) {
            prices = priceRepository.loadAll(jobInstanceId);
        }

        long cash = retryExecutor.execute(
                () -> cashBalanceClient.getCash(target.userId())
        );
        long seedCapital = retryExecutor.execute(
                () -> seedCapitalProvider.getSeedCapital(target.userId(), businessDate)
        );
        SnapshotCommand.CalculationContext context = new SnapshotCommand.CalculationContext(
                businessDate,
                cash,
                seedCapital,
                prices,
                keyFactory.create(businessDate, target.userId())
        );
        return calculator.calculate(target, context);
    }
}

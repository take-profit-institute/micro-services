package org.profit.candle.batch.portfolio.eod.policy;

import org.profit.candle.batch.portfolio.eod.exception.EodBatchException;
import org.profit.candle.batch.portfolio.eod.exception.EodBatchErrorCode;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;
import org.profit.candle.batch.portfolio.eod.model.SnapshotCommand;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.springframework.stereotype.Component;

@Component
public class SnapshotCalculator {

    public SnapshotCommand calculate(
            SnapshotTarget target,
            SnapshotCommand.CalculationContext context
    ) {
        if (context.cash() < 0) {
            throw new EodBatchException(EodBatchErrorCode.CASH_BALANCE_INVALID);
        }
        if (context.seedCapital() <= 0) {
            throw new EodBatchException(EodBatchErrorCode.SEED_CAPITAL_INVALID);
        }

        long stockValue = 0L;
        for (SnapshotTarget.Holding holding : target.holdings()) {
            if (holding.quantity() <= 0) {
                throw new EodBatchException(EodBatchErrorCode.HOLDING_QUANTITY_INVALID);
            }

            ClosingPrice price = context.closingPrices().get(holding.symbol());
            if (price == null || price.price() <= 0) {
                throw new EodBatchException(EodBatchErrorCode.CLOSING_PRICE_INVALID);
            }

            stockValue = Math.addExact(
                    stockValue,
                    Math.multiplyExact(holding.quantity(), price.price())
            );
        }

        return new SnapshotCommand(
                target.userId(),
                context.businessDate(),
                Math.addExact(context.cash(), stockValue),
                stockValue,
                context.seedCapital(),
                context.idempotencyKey()
        );
    }
}

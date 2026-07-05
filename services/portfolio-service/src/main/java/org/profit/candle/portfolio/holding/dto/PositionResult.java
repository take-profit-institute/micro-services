package org.profit.candle.portfolio.holding.dto;

import org.profit.candle.portfolio.holding.entity.HoldingEntity;

public record PositionResult(
        String symbol,
        long quantity,
        long averagePrice
) {
    public static PositionResult from(HoldingEntity entity) {
        return new PositionResult(entity.symbol(), entity.quantity(), entity.averagePrice());
    }
}

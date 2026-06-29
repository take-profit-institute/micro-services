package org.profit.candle.portfolio.holding.dto;

import org.profit.candle.portfolio.holding.entity.HoldingEntity;

public record HoldingResult(
        String userId,
        String symbol,
        String name,
        long quantity,
        long averagePrice,
        long bookValue,
        long realizedProfit,
        boolean active,
        String sector,
        String market
) {
    public static HoldingResult from(HoldingEntity entity) {
        return new HoldingResult(
                entity.userId(), entity.symbol(), entity.name(),
                entity.quantity(), entity.averagePrice(), entity.bookValue(),
                entity.realizedProfit(), entity.active(),
                entity.sector(), entity.market());
    }
}

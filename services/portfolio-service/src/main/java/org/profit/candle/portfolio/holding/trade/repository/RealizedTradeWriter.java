package org.profit.candle.portfolio.holding.trade.repository;

import org.profit.candle.portfolio.holding.trade.entity.RealizedTradeEntity;

public interface RealizedTradeWriter {
    RealizedTradeEntity save(RealizedTradeEntity entity);
}

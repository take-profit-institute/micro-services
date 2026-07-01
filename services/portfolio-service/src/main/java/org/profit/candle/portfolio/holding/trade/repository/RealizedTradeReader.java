package org.profit.candle.portfolio.holding.trade.repository;

import org.profit.candle.portfolio.holding.trade.entity.RealizedTradeEntity;

import java.util.List;

public interface RealizedTradeReader {
    List<RealizedTradeEntity> findByUserId(String userId);
}

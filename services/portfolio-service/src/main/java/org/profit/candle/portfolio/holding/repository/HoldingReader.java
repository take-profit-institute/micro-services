package org.profit.candle.portfolio.holding.repository;

import org.profit.candle.portfolio.holding.entity.HoldingEntity;

import java.util.List;
import java.util.Optional;

public interface HoldingReader {
    Optional<HoldingEntity> findByUserIdAndSymbol(String userId, String symbol);
    List<HoldingEntity> findByUserId(String userId);
    List<HoldingEntity> findActiveByUserId(String userId);
}

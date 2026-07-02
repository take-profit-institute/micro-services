package org.profit.candle.portfolio.holding.repository;

import org.profit.candle.portfolio.holding.entity.HoldingEntity;

public interface HoldingWriter {
    HoldingEntity save(HoldingEntity entity);
}

package org.profit.candle.market.repository;

import org.profit.candle.market.entity.Tick;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TickRepository extends JpaRepository<Tick, Long> {
}

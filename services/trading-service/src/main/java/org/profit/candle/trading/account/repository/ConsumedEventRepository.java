package org.profit.candle.trading.account.repository;

import org.profit.candle.trading.account.entity.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {
}
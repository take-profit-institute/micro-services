package org.profit.candle.trading.domain.repository;

import org.profit.candle.trading.domain.entity.AccountBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountBalanceRepository extends JpaRepository<AccountBalanceEntity, String> {
}

package org.profit.candle.trading.account.repository;

import org.profit.candle.trading.account.entity.AccountBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountBalanceRepository extends JpaRepository<AccountBalanceEntity, String> {
}

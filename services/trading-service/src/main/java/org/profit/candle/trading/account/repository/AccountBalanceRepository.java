package org.profit.candle.trading.account.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.profit.candle.trading.account.entity.AccountBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountBalanceRepository extends JpaRepository<AccountBalanceEntity, String> {

    /** 잔고를 잠그고 조회한다. reserve/release 등 동시성 위험이 있는 변경 전에 호출한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AccountBalanceEntity b where b.userId = :userId")
    Optional<AccountBalanceEntity> findByIdForUpdate(String userId);
}
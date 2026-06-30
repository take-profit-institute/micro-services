package org.profit.candle.trading.account.repository;

import jakarta.persistence.LockModeType;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity  a where a.userId = :userId")
    Optional<AccountEntity> findByUserIdForUpdate(UUID userId);
}

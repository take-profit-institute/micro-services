package org.profit.candle.auth.admin.repository;

import java.util.Optional;
import java.util.UUID;
import org.profit.candle.auth.admin.entity.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, UUID> {
    Optional<AdminAccount> findByUsername(String username);
    boolean existsByUsername(String username);
}

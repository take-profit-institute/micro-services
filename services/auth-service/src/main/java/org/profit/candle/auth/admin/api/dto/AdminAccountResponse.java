package org.profit.candle.auth.admin.api.dto;

import java.time.Instant;
import org.profit.candle.auth.admin.entity.AdminAccount;
import org.profit.candle.auth.admin.entity.AdminRole;
import org.profit.candle.auth.admin.entity.AdminStatus;

public record AdminAccountResponse(
        String id,
        String username,
        String displayName,
        AdminRole role,
        AdminStatus status,
        Instant lastLoginAt,
        Instant createdAt) {

    public static AdminAccountResponse from(AdminAccount account) {
        return new AdminAccountResponse(
                account.id().toString(),
                account.username(),
                account.displayName(),
                account.role(),
                account.status(),
                account.lastLoginAt(),
                account.createdAt());
    }
}

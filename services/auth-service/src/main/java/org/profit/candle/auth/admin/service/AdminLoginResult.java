package org.profit.candle.auth.admin.service;

import java.util.UUID;
import org.profit.candle.auth.admin.entity.AdminAccount;
import org.profit.candle.auth.admin.entity.AdminRole;
import org.profit.candle.auth.token.service.IssuedTokens;

public record AdminLoginResult(
        IssuedTokens tokens,
        UUID adminId,
        String username,
        String displayName,
        AdminRole role) {

    public static AdminLoginResult of(IssuedTokens tokens, AdminAccount admin) {
        return new AdminLoginResult(tokens, admin.id(), admin.username(), admin.displayName(), admin.role());
    }
}

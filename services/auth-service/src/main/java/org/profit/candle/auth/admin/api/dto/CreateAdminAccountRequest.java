package org.profit.candle.auth.admin.api.dto;

import org.profit.candle.auth.admin.entity.AdminRole;

public record CreateAdminAccountRequest(String username, String password, String displayName, AdminRole role) {
}

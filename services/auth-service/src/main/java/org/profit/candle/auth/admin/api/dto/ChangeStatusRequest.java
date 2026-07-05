package org.profit.candle.auth.admin.api.dto;

import org.profit.candle.auth.admin.entity.AdminStatus;

public record ChangeStatusRequest(AdminStatus status) {
}

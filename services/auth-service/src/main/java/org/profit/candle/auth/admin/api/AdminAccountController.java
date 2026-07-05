package org.profit.candle.auth.admin.api;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.admin.api.dto.AdminAccountResponse;
import org.profit.candle.auth.admin.api.dto.ChangeStatusRequest;
import org.profit.candle.auth.admin.api.dto.CreateAdminAccountRequest;
import org.profit.candle.auth.admin.api.dto.ResetPasswordRequest;
import org.profit.candle.auth.admin.entity.AdminRole;
import org.profit.candle.auth.admin.service.AdminAccountService;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 계정 CRUD. SUPER_ADMIN 전용.
 * 게이트웨이가 /api/v1/admin/** 를 ROLE_SUPER_ADMIN으로 1차 인가하고,
 * 여기서는 게이트웨이가 주입한 X-Account-Role 헤더로 2차 방어한다.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAccountResponse create(@RequestHeader(value = "X-Account-Role", required = false) String callerRole,
            @RequestBody CreateAdminAccountRequest request) {
        requireSuperAdmin(callerRole);
        return adminAccountService.create(request);
    }

    @GetMapping
    public List<AdminAccountResponse> list(
            @RequestHeader(value = "X-Account-Role", required = false) String callerRole) {
        requireSuperAdmin(callerRole);
        return adminAccountService.list();
    }

    @PatchMapping("/{id}/status")
    public AdminAccountResponse changeStatus(
            @RequestHeader(value = "X-Account-Role", required = false) String callerRole,
            @PathVariable String id, @RequestBody ChangeStatusRequest request) {
        requireSuperAdmin(callerRole);
        return adminAccountService.changeStatus(parseId(id), request == null ? null : request.status());
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestHeader(value = "X-Account-Role", required = false) String callerRole,
            @PathVariable String id, @RequestBody ResetPasswordRequest request) {
        requireSuperAdmin(callerRole);
        adminAccountService.resetPassword(parseId(id), request == null ? null : request.password());
    }

    private void requireSuperAdmin(String callerRole) {
        if (!AdminRole.SUPER_ADMIN.name().equals(callerRole)) {
            throw new AuthException(AuthErrorCode.ADMIN_FORBIDDEN);
        }
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new AuthException(AuthErrorCode.INVALID_ADMIN_REQUEST, exception);
        }
    }
}

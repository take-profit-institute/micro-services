package org.profit.candle.auth.admin.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.admin.api.dto.AdminAccountResponse;
import org.profit.candle.auth.admin.api.dto.CreateAdminAccountRequest;
import org.profit.candle.auth.admin.entity.AdminAccount;
import org.profit.candle.auth.admin.entity.AdminRole;
import org.profit.candle.auth.admin.entity.AdminStatus;
import org.profit.candle.auth.admin.repository.AdminAccountRepository;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminAccountResponse create(CreateAdminAccountRequest request) {
        if (isBlank(request.username()) || isBlank(request.password()) || isBlank(request.displayName())
                || request.role() == null) {
            throw new AuthException(AuthErrorCode.INVALID_ADMIN_REQUEST);
        }
        if (adminAccountRepository.existsByUsername(request.username())) {
            throw new AuthException(AuthErrorCode.ADMIN_USERNAME_ALREADY_EXISTS);
        }
        Instant now = Instant.now();
        AdminAccount account = adminAccountRepository.save(new AdminAccount(
                UUID.randomUUID(),
                request.username(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                request.role(),
                AdminStatus.ACTIVE,
                now));
        return AdminAccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> list() {
        return adminAccountRepository.findAll().stream().map(AdminAccountResponse::from).toList();
    }

    @Transactional
    public AdminAccountResponse changeStatus(UUID id, AdminStatus status) {
        if (status == null) {
            throw new AuthException(AuthErrorCode.INVALID_ADMIN_REQUEST);
        }
        AdminAccount account = find(id);
        account.changeStatus(status, Instant.now());
        return AdminAccountResponse.from(account);
    }

    @Transactional
    public void resetPassword(UUID id, String rawPassword) {
        if (isBlank(rawPassword)) {
            throw new AuthException(AuthErrorCode.INVALID_ADMIN_REQUEST);
        }
        AdminAccount account = find(id);
        account.changePassword(passwordEncoder.encode(rawPassword), Instant.now());
    }

    private AdminAccount find(UUID id) {
        return adminAccountRepository.findById(id)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ADMIN_ACCOUNT_NOT_FOUND));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package org.profit.candle.auth.admin.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.admin.entity.AdminAccount;
import org.profit.candle.auth.admin.repository.AdminAccountRepository;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.token.entity.PrincipalType;
import org.profit.candle.auth.token.service.AuthTokenIssuer;
import org.profit.candle.auth.token.service.IssuedTokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminLoginService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenIssuer tokenIssuer;
    private final AuthProperties properties;

    @Transactional
    public AdminLoginResult login(String username, String rawPassword) {
        Instant now = Instant.now();
        // 계정 미존재/비밀번호 불일치는 동일한 에러로 응답해 아이디 존재 여부 노출을 막는다.
        AdminAccount admin = adminAccountRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_ADMIN_CREDENTIALS));
        if (admin.isLocked(now)) {
            throw new AuthException(AuthErrorCode.ADMIN_ACCOUNT_LOCKED);
        }
        if (!admin.isActive()) {
            throw new AuthException(AuthErrorCode.ADMIN_ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(rawPassword, admin.passwordHash())) {
            admin.recordLoginFailure(now, properties.admin().maxFailedAttempts(), properties.admin().lockDuration());
            throw new AuthException(AuthErrorCode.INVALID_ADMIN_CREDENTIALS);
        }
        admin.recordLoginSuccess(now);
        IssuedTokens tokens = tokenIssuer.issue(admin.id(), "", admin.role().name(), PrincipalType.ADMIN);
        return AdminLoginResult.of(tokens, admin);
    }
}

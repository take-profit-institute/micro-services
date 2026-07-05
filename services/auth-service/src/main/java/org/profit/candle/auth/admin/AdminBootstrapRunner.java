package org.profit.candle.auth.admin;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.admin.entity.AdminAccount;
import org.profit.candle.auth.admin.entity.AdminRole;
import org.profit.candle.auth.admin.entity.AdminStatus;
import org.profit.candle.auth.admin.repository.AdminAccountRepository;
import org.profit.candle.auth.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 최초 SUPER_ADMIN 부트스트랩.
 * auth.admin.bootstrap.username/password가 설정되어 있고 관리자 계정이 0건일 때만 1개 생성한다.
 * 평문 비밀번호는 런타임 env로만 전달되고 DB에는 BCrypt 해시만 저장된다.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.Admin.Bootstrap bootstrap = properties.admin() == null ? null : properties.admin().bootstrap();
        if (bootstrap == null || isBlank(bootstrap.username()) || isBlank(bootstrap.password())) {
            return;
        }
        if (adminAccountRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        String displayName = isBlank(bootstrap.displayName()) ? "Super Admin" : bootstrap.displayName();
        adminAccountRepository.save(new AdminAccount(
                UUID.randomUUID(),
                bootstrap.username(),
                passwordEncoder.encode(bootstrap.password()),
                displayName,
                AdminRole.SUPER_ADMIN,
                AdminStatus.ACTIVE,
                now));
        log.info("Bootstrapped SUPER_ADMIN account: username={}", bootstrap.username());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

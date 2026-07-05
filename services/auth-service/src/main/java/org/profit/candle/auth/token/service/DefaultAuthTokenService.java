package org.profit.candle.auth.token.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.admin.entity.AdminAccount;
import org.profit.candle.auth.admin.repository.AdminAccountRepository;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.identity.repository.OAuthAccountRepository;
import org.profit.candle.auth.token.entity.PrincipalType;
import org.profit.candle.auth.token.entity.RefreshToken;
import org.profit.candle.auth.token.repository.RefreshTokenRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultAuthTokenService implements AuthTokenIssuer, RefreshTokenService {

    private final AuthProperties properties;
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final AdminAccountRepository adminAccountRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public IssuedTokens issue(UUID subject, String email, String role, PrincipalType type) {
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plus(properties.jwt().accessTokenTtl());
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer(properties.jwt().issuer())
                        .subject(subject.toString())
                        .issuedAt(now)
                        .expiresAt(accessExpiresAt)
                        .id(UUID.randomUUID().toString())
                        .claim("role", role)
                        .claim("email", email)
                        .build()))
                .getTokenValue();
        String refreshToken = newRefreshToken();
        refreshTokenRepository.save(new RefreshToken(UUID.randomUUID(), subject, hash(refreshToken),
                type, now.plus(properties.jwt().refreshTokenTtl())));
        return new IssuedTokens(accessToken, refreshToken, properties.jwt().accessTokenTtl().toSeconds());
    }

    @Override
    @Transactional
    public IssuedTokens rotate(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> token.usableAt(Instant.now()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        stored.revoke(Instant.now());
        if (stored.principalType() == PrincipalType.ADMIN) {
            AdminAccount admin = adminAccountRepository.findById(stored.userId())
                    .filter(AdminAccount::isActive)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
            return issue(admin.id(), "", admin.role().name(), PrincipalType.ADMIN);
        }
        String email = oAuthAccountRepository.findById(stored.userId())
                .map(account -> account.email())
                .orElse("");
        return issue(stored.userId(), email, "USER", PrincipalType.USER);
    }

    @Override
    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

package org.profit.candle.auth.token.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
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
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public IssuedTokens issue(UUID userId) {
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plus(properties.jwt().accessTokenTtl());
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(properties.jwt().issuer()).subject(userId.toString())
                        .issuedAt(now).expiresAt(accessExpiresAt).id(UUID.randomUUID().toString())
                        .claim("role", "USER").build())).getTokenValue();
        String refreshToken = newRefreshToken();
        refreshTokenRepository.save(new RefreshToken(UUID.randomUUID(), userId, hash(refreshToken),
                now.plus(properties.jwt().refreshTokenTtl())));
        return new IssuedTokens(accessToken, refreshToken, properties.jwt().accessTokenTtl().toSeconds());
    }

    @Override
    @Transactional
    public IssuedTokens rotate(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> token.usableAt(Instant.now()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        stored.revoke(Instant.now());
        return issue(stored.userId());
    }

    @Override
    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken)).ifPresent(token -> token.revoke(Instant.now()));
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

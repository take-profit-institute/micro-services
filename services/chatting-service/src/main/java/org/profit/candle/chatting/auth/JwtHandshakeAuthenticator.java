package org.profit.candle.chatting.auth;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.chatting.config.ChatProperties;
import org.springframework.stereotype.Component;

/**
 * HMAC(HS256) JWT 자체 검증 구현. 게이트웨이와 동일한 시크릿을 공유한다.
 *
 * <p>accountId는 JWT {@code sub} 클레임에서 추출한다(게이트웨이 {@code X-Account-Id} 주입과 동일 의미).
 */
@Slf4j
@Component
public class JwtHandshakeAuthenticator implements HandshakeAuthenticator {

    private final JWSVerifier verifier;

    public JwtHandshakeAuthenticator(ChatProperties properties) {
        try {
            this.verifier = new MACVerifier(properties.jwt().hmacSecret().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC verifier 초기화 실패 (시크릿 길이가 32바이트 미만일 수 있음)", e);
        }
    }

    @Override
    public Optional<String> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                return Optional.empty();
            }
            Date expiration = jwt.getJWTClaimsSet().getExpirationTime();
            if (expiration != null && expiration.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            return Optional.ofNullable(jwt.getJWTClaimsSet().getSubject());
        } catch (Exception e) {
            log.debug("WS 핸드셰이크 JWT 검증 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

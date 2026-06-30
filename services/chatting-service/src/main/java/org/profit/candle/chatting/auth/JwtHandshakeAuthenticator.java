package org.profit.candle.chatting.auth;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.chatting.config.ChatProperties;
import org.springframework.stereotype.Component;

/**
 * HMAC(HS256) JWT 자체 검증 구현. auth-service와 동일한 시크릿·issuer를 공유한다.
 *
 * <p>검증 범위를 시스템 표준(auth-service 발급 토큰)에 맞춘다:
 * <ul>
 *   <li><b>서명</b> — 공유 HMAC 시크릿</li>
 *   <li><b>만료(exp)</b> — <b>필수</b>. exp가 없으면 거부(영구 토큰 차단), 만료 시 거부(소량 클록 스큐 허용)</li>
 *   <li><b>발급자(iss)</b> — 설정된 기대 issuer와 일치해야 함</li>
 * </ul>
 *
 * <p>accountId는 JWT {@code sub} 클레임에서 추출한다(게이트웨이 {@code X-Account-Id} 주입과 동일 의미).
 */
@Slf4j
@Component
public class JwtHandshakeAuthenticator implements HandshakeAuthenticator {

    /** 시계 오차 허용(초) — 만료 직전 토큰이 약간의 드리프트로 거부되는 것을 방지. */
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final JWSVerifier verifier;
    private final String expectedIssuer;

    public JwtHandshakeAuthenticator(ChatProperties properties) {
        try {
            this.verifier = new MACVerifier(properties.jwt().hmacSecret().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC verifier 초기화 실패 (시크릿 길이가 32바이트 미만일 수 있음)", e);
        }
        this.expectedIssuer = properties.jwt().issuer() == null ? "" : properties.jwt().issuer().trim();
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
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // exp 필수: 누락(영구 토큰)·만료 모두 거부
            Date expiration = claims.getExpirationTime();
            if (expiration == null
                    || expiration.toInstant().plusSeconds(CLOCK_SKEW_SECONDS).isBefore(Instant.now())) {
                return Optional.empty();
            }
            // issuer 검증: 기대 issuer가 설정돼 있으면 일치해야 함
            if (!expectedIssuer.isEmpty() && !expectedIssuer.equals(claims.getIssuer())) {
                return Optional.empty();
            }
            return Optional.ofNullable(claims.getSubject());
        } catch (Exception e) {
            log.debug("WS 핸드셰이크 JWT 검증 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

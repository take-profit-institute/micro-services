package org.profit.candle.chatting.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.chatting.config.ChatProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RS256 + JWKS 기반 WS 핸드셰이크 JWT 자체 검증. auth-service의 공개키(JWKS)로 서명을 검증한다.
 * (WS는 API Gateway를 거치지 않고 ALB로 직결되므로 chatting이 직접 검증해야 한다.)
 *
 * <p>검증 범위:
 * <ul>
 *   <li><b>서명</b> — auth-service JWKS의 공개키(kid로 선택), RS256</li>
 *   <li><b>만료(exp)</b> — <b>필수</b>. 누락(영구 토큰)·만료 모두 거부(소량 클록 스큐 허용)</li>
 *   <li><b>발급자(iss)</b> — 설정 시 일치 필수</li>
 *   <li><b>대상(aud)</b> — 설정 시 포함 필수</li>
 * </ul>
 * accountId는 {@code sub} 클레임에서 추출한다(게이트웨이 {@code X-Account-Id} 주입과 동일 의미).
 */
@Slf4j
@Component
public class JwtHandshakeAuthenticator implements HandshakeAuthenticator {

    /** 시계 오차 허용(초). */
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final String expectedIssuer;
    private final String expectedAudience;

    @Autowired
    public JwtHandshakeAuthenticator(ChatProperties properties) {
        this(remoteJwkSource(properties.jwt().jwkSetUri()), properties);
    }

    /** 테스트에서 JWKSource(로컬 JWKSet)를 직접 주입하기 위한 생성자. */
    JwtHandshakeAuthenticator(JWKSource<SecurityContext> jwkSource, ChatProperties properties) {
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        this.jwtProcessor = processor;
        this.expectedIssuer = properties.jwt().issuer() == null ? "" : properties.jwt().issuer().trim();
        this.expectedAudience = properties.jwt().audience() == null ? "" : properties.jwt().audience().trim();
    }

    private static JWKSource<SecurityContext> remoteJwkSource(String jwkSetUri) {
        try {
            return JWKSourceBuilder.<SecurityContext>create(URI.create(jwkSetUri).toURL()).build();
        } catch (Exception e) {
            throw new IllegalStateException("JWKS source 초기화 실패: " + jwkSetUri, e);
        }
    }

    @Override
    public Optional<String> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            JWTClaimsSet claims = jwtProcessor.process(token, null);

            Date expiration = claims.getExpirationTime();
            if (expiration == null
                    || expiration.toInstant().plusSeconds(CLOCK_SKEW_SECONDS).isBefore(Instant.now())) {
                return Optional.empty();
            }
            if (!expectedIssuer.isEmpty() && !expectedIssuer.equals(claims.getIssuer())) {
                return Optional.empty();
            }
            List<String> audience = claims.getAudience();
            if (!expectedAudience.isEmpty() && (audience == null || !audience.contains(expectedAudience))) {
                return Optional.empty();
            }
            return Optional.ofNullable(claims.getSubject());
        } catch (Exception e) {
            log.debug("WS 핸드셰이크 JWT 검증 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

package org.profit.candle.auth.api;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.config.AuthProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * OIDC discovery + JWKS 노출. AWS API Gateway JWT authorizer 와 다운스트림 검증자(gateway·chatting·BFF)가
 * 여기서 공개키를 가져와 RS256 access token 을 검증한다. 인증 없이 접근 가능해야 한다
 * (AuthSecurityConfiguration 이 anyRequest permitAll 이라 이미 공개).
 */
@RestController
@RequiredArgsConstructor
public class WellKnownController {

    private final JWKSet jwkSet;
    private final AuthProperties properties;

    /** 공개키 셋. 개인키 성분은 toPublicJWKSet 로 제거된다. */
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(jwkSet.toPublicJWKSet().toJSONObject());
    }

    /** OIDC discovery. APIGW authorizer 는 issuer 에서 이 문서를 읽어 jwks_uri 를 찾는다. */
    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> openidConfiguration() {
        String issuer = properties.jwt().issuer();
        Map<String, Object> config = Map.of(
                "issuer", issuer,
                "jwks_uri", issuer + "/.well-known/jwks.json",
                "id_token_signing_alg_values_supported", List.of("RS256"),
                "response_types_supported", List.of("token"),
                "subject_types_supported", List.of("public"));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(config);
    }
}

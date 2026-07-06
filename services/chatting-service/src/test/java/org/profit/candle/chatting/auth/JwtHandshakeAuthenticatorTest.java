package org.profit.candle.chatting.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.chatting.config.ChatProperties;

import static org.assertj.core.api.Assertions.assertThat;

class JwtHandshakeAuthenticatorTest {

    static final String ISSUER = "candle-auth-test";
    static final String AUDIENCE = "candle-api";
    static final String ACCOUNT_ID = "account-1";

    // auth-service 서명 키(private) + JWKS(public). 다른 키(WRONG)는 서명 위조 테스트용.
    static final RSAKey SIGNING_KEY = rsaKey();
    static final RSAKey WRONG_KEY = rsaKey();
    static final JWKSource<SecurityContext> JWKS =
            new ImmutableJWKSet<>(new JWKSet(SIGNING_KEY.toPublicJWK()));

    final JwtHandshakeAuthenticator authenticator =
            new JwtHandshakeAuthenticator(JWKS, properties(ISSUER, AUDIENCE));

    @Test
    void authenticate_validToken_returnsSubject() throws Exception {
        String token = token(SIGNING_KEY, ACCOUNT_ID, ISSUER, AUDIENCE, Instant.now().plusSeconds(60));
        assertThat(authenticator.authenticate(token)).contains(ACCOUNT_ID);
    }

    @Test
    void authenticate_blankToken_returnsEmpty() {
        assertThat(authenticator.authenticate("")).isEmpty();
        assertThat(authenticator.authenticate(null)).isEmpty();
    }

    @Test
    void authenticate_invalidSignature_returnsEmpty() throws Exception {
        // JWKS에 없는 키로 서명 → 검증 실패
        String token = token(WRONG_KEY, ACCOUNT_ID, ISSUER, AUDIENCE, Instant.now().plusSeconds(60));
        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_expiredToken_returnsEmpty() throws Exception {
        String token = token(SIGNING_KEY, ACCOUNT_ID, ISSUER, AUDIENCE, Instant.now().minusSeconds(120));
        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_missingExpiration_returnsEmpty() throws Exception {
        String token = token(SIGNING_KEY, ACCOUNT_ID, ISSUER, AUDIENCE, null);
        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_wrongIssuer_returnsEmpty() throws Exception {
        String token = token(SIGNING_KEY, ACCOUNT_ID, "evil-issuer", AUDIENCE, Instant.now().plusSeconds(60));
        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_wrongAudience_returnsEmpty() throws Exception {
        String token = token(SIGNING_KEY, ACCOUNT_ID, ISSUER, "other-api", Instant.now().plusSeconds(60));
        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_missingSubject_returnsEmpty() throws Exception {
        String token = token(SIGNING_KEY, null, ISSUER, AUDIENCE, Instant.now().plusSeconds(60));
        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_issuerNotConfigured_skipsIssuerCheck() throws Exception {
        JwtHandshakeAuthenticator noIssuer = new JwtHandshakeAuthenticator(JWKS, properties("", ""));
        String token = token(SIGNING_KEY, ACCOUNT_ID, "anything", "anything", Instant.now().plusSeconds(60));
        assertThat(noIssuer.authenticate(token)).contains(ACCOUNT_ID);
    }

    private static ChatProperties properties(String issuer, String audience) {
        return new ChatProperties(
                new ChatProperties.Jwt("http://unused/.well-known/jwks.json", issuer, audience),
                new ChatProperties.Room(500, Duration.ofHours(2)),
                new ChatProperties.Cors(List.of()));
    }

    private static String token(RSAKey key, String subject, String issuer, String audience, Instant expiresAt)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        if (subject != null) {
            claims.subject(subject);
        }
        if (issuer != null) {
            claims.issuer(issuer);
        }
        if (audience != null) {
            claims.audience(audience);
        }
        if (expiresAt != null) {
            claims.expirationTime(Date.from(expiresAt));
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey rsaKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyIDFromThumbprint(true)
                    .generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

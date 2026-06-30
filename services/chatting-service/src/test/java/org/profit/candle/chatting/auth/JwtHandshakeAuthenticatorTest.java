package org.profit.candle.chatting.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
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

    static final String SECRET = "12345678901234567890123456789012";
    static final String ISSUER = "candle-auth-test";
    static final String ACCOUNT_ID = "account-1";

    final JwtHandshakeAuthenticator authenticator = new JwtHandshakeAuthenticator(properties(SECRET, ISSUER));

    @Test
    void authenticate_validToken_returnsSubject() throws Exception {
        String token = token(SECRET, ACCOUNT_ID, ISSUER, Instant.now().plusSeconds(60));

        assertThat(authenticator.authenticate(token)).contains(ACCOUNT_ID);
    }

    @Test
    void authenticate_blankToken_returnsEmpty() {
        assertThat(authenticator.authenticate("")).isEmpty();
        assertThat(authenticator.authenticate(null)).isEmpty();
    }

    @Test
    void authenticate_invalidSignature_returnsEmpty() throws Exception {
        String token = token("abcdefghijklmnopqrstuvwxyz123456", ACCOUNT_ID, ISSUER, Instant.now().plusSeconds(60));

        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_expiredToken_returnsEmpty() throws Exception {
        // 클록 스큐(30s)를 넘겨 확실히 만료
        String token = token(SECRET, ACCOUNT_ID, ISSUER, Instant.now().minusSeconds(120));

        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_missingExpiration_returnsEmpty() throws Exception {
        // exp 누락(영구 토큰)은 거부해야 한다
        String token = token(SECRET, ACCOUNT_ID, ISSUER, null);

        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_wrongIssuer_returnsEmpty() throws Exception {
        String token = token(SECRET, ACCOUNT_ID, "evil-issuer", Instant.now().plusSeconds(60));

        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_missingIssuer_returnsEmpty() throws Exception {
        String token = token(SECRET, ACCOUNT_ID, null, Instant.now().plusSeconds(60));

        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_missingSubject_returnsEmpty() throws Exception {
        String token = token(SECRET, null, ISSUER, Instant.now().plusSeconds(60));

        assertThat(authenticator.authenticate(token)).isEmpty();
    }

    @Test
    void authenticate_issuerNotConfigured_skipsIssuerCheck() throws Exception {
        // issuer 미설정(빈 값)이면 어떤 iss든 통과(escape hatch)
        JwtHandshakeAuthenticator noIssuer = new JwtHandshakeAuthenticator(properties(SECRET, ""));
        String token = token(SECRET, ACCOUNT_ID, "anything", Instant.now().plusSeconds(60));

        assertThat(noIssuer.authenticate(token)).contains(ACCOUNT_ID);
    }

    private static ChatProperties properties(String secret, String issuer) {
        return new ChatProperties(
                new ChatProperties.Jwt(secret, issuer),
                new ChatProperties.Room(500, Duration.ofHours(2)),
                new ChatProperties.Cors(List.of()));
    }

    private static String token(String secret, String subject, String issuer, Instant expiresAt) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        if (subject != null) {
            claims.subject(subject);
        }
        if (issuer != null) {
            claims.issuer(issuer);
        }
        if (expiresAt != null) {
            claims.expirationTime(Date.from(expiresAt));
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(secret.getBytes()));
        return jwt.serialize();
    }
}

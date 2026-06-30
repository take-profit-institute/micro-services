package org.profit.candle.chatting.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.chatting.config.ChatProperties;

import static org.assertj.core.api.Assertions.assertThat;

class JwtHandshakeAuthenticatorTest {

    static final String SECRET = "12345678901234567890123456789012";
    static final String ACCOUNT_ID = "account-1";

    JwtHandshakeAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new JwtHandshakeAuthenticator(properties(SECRET));
    }

    @Test
    void authenticate_validToken_returnsSubject() throws Exception {
        String token = token(SECRET, ACCOUNT_ID, Instant.now().plusSeconds(60));

        Optional<String> accountId = authenticator.authenticate(token);

        assertThat(accountId).contains(ACCOUNT_ID);
    }

    @Test
    void authenticate_blankToken_returnsEmpty() {
        assertThat(authenticator.authenticate("")).isEmpty();
    }

    @Test
    void authenticate_invalidSignature_returnsEmpty() throws Exception {
        String token = token("abcdefghijklmnopqrstuvwxyz123456", ACCOUNT_ID, Instant.now().plusSeconds(60));

        Optional<String> accountId = authenticator.authenticate(token);

        assertThat(accountId).isEmpty();
    }

    @Test
    void authenticate_expiredToken_returnsEmpty() throws Exception {
        String token = token(SECRET, ACCOUNT_ID, Instant.now().minusSeconds(1));

        Optional<String> accountId = authenticator.authenticate(token);

        assertThat(accountId).isEmpty();
    }

    @Test
    void authenticate_missingSubject_returnsEmpty() throws Exception {
        String token = token(SECRET, null, Instant.now().plusSeconds(60));

        Optional<String> accountId = authenticator.authenticate(token);

        assertThat(accountId).isEmpty();
    }

    private static ChatProperties properties(String secret) {
        return new ChatProperties(
                new ChatProperties.Jwt(secret),
                new ChatProperties.Room(500, Duration.ofHours(2)));
    }

    private static String token(String secret, String subject, Instant expiresAt) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .expirationTime(Date.from(expiresAt));
        if (subject != null) {
            claims.subject(subject);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(secret.getBytes()));
        return jwt.serialize();
    }
}

package org.profit.candle.auth.token.entity;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    void usableAt_notRevokedNotExpired_returnsTrue() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash",
                Instant.now().plusSeconds(3600));

        assertThat(token.usableAt(Instant.now())).isTrue();
    }

    @Test
    void usableAt_expired_returnsFalse() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash",
                Instant.now().minusSeconds(1));

        assertThat(token.usableAt(Instant.now())).isFalse();
    }

    @Test
    void usableAt_afterRevoke_returnsFalse() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash",
                Instant.now().plusSeconds(3600));
        token.revoke(Instant.now());

        assertThat(token.usableAt(Instant.now())).isFalse();
    }

    @Test
    void revoke_alreadyRevoked_remainsUnusable() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "hash",
                Instant.now().plusSeconds(3600));
        token.revoke(Instant.now());
        token.revoke(Instant.now().plusSeconds(1));

        assertThat(token.usableAt(Instant.now())).isFalse();
    }
}

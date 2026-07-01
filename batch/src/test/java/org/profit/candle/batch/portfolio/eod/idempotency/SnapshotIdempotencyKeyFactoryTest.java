package org.profit.candle.batch.portfolio.eod.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotIdempotencyKeyFactoryTest {

    private final SnapshotIdempotencyKeyFactory factory =
            new SnapshotIdempotencyKeyFactory();

    @Test
    void shouldCreateDeterministicKeyForBusinessDateAndUser() {
        LocalDate date = LocalDate.of(2026, 6, 29);

        String first = factory.create(date, "user-1");
        String second = factory.create(date, "user-1");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(36);
        assertThat(UUID.fromString(first).version()).isEqualTo(3);
    }

    @Test
    void shouldChangeKeyWhenDateChanges() {
        assertThat(factory.create(LocalDate.of(2026, 6, 28), "user-1"))
                .isNotEqualTo(factory.create(LocalDate.of(2026, 6, 29), "user-1"));
    }
}

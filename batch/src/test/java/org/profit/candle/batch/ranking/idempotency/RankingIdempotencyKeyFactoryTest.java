package org.profit.candle.batch.ranking.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankingIdempotencyKeyFactoryTest {

    private final RankingIdempotencyKeyFactory factory = new RankingIdempotencyKeyFactory();

    /** 같은 거래일은 동일한 canonical UUID를 생성한다. */
    @Test
    void createsStableCanonicalUuidForSameDate() {
        LocalDate rankingDate = LocalDate.of(2026, 7, 6);

        String first = factory.create(rankingDate);
        String second = factory.create(rankingDate);

        assertThat(first).isEqualTo(second);
        assertThat(UUID.fromString(first).toString()).isEqualTo(first);
    }

    /** 거래일이 다르면 다른 멱등성 키를 생성한다. */
    @Test
    void createsDifferentKeyForDifferentDate() {
        assertThat(factory.create(LocalDate.of(2026, 7, 6)))
                .isNotEqualTo(factory.create(LocalDate.of(2026, 7, 7)));
    }
}

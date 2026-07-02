package org.profit.candle.batch.portfolio.eod.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.profit.candle.batch.portfolio.eod.client.SnapshotTargetClient;
import org.profit.candle.batch.portfolio.eod.model.SnapshotTarget;
import org.profit.candle.batch.portfolio.eod.policy.EodRetryExecutor;
import org.springframework.batch.infrastructure.item.ExecutionContext;

class SnapshotTargetItemReaderTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 6, 29);

    @Test
    void shouldResumeAtCommittedIndexInsideCursorPage() throws Exception {
        FakeTargetClient client = new FakeTargetClient();
        ExecutionContext context = new ExecutionContext();
        SnapshotTargetItemReader first = new SnapshotTargetItemReader(
                client,
                new EodRetryExecutor(),
                BUSINESS_DATE,
                2
        );

        first.open(context);
        assertThat(first.read().userId()).isEqualTo("user-1");
        first.update(context);

        SnapshotTargetItemReader restarted = new SnapshotTargetItemReader(
                client,
                new EodRetryExecutor(),
                BUSINESS_DATE,
                2
        );
        restarted.open(context);

        assertThat(restarted.read().userId()).isEqualTo("user-2");
        assertThat(restarted.read().userId()).isEqualTo("user-3");
        assertThat(restarted.read()).isNull();
        assertThat(client.tokens).containsExactly("", "", "next");
    }

    private static final class FakeTargetClient implements SnapshotTargetClient {

        private final List<String> tokens = new ArrayList<>();

        @Override
        public SnapshotTarget.Page loadTargets(
                LocalDate businessDate,
                String pageToken,
                int pageSize
        ) {
            tokens.add(pageToken);
            if (pageToken.isBlank()) {
                return new SnapshotTarget.Page(
                        List.of(target("user-1"), target("user-2")),
                        "next"
                );
            }
            return new SnapshotTarget.Page(List.of(target("user-3")), "");
        }

        private SnapshotTarget target(String userId) {
            return new SnapshotTarget(userId, List.of());
        }
    }
}

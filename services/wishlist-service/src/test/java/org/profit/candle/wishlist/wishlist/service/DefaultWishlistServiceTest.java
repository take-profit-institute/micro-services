package org.profit.candle.wishlist.wishlist.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.profit.candle.wishlist.event.OutboxWriter;
import org.profit.candle.wishlist.wishlist.dto.AddWishlistItemCommand;
import org.profit.candle.wishlist.wishlist.dto.RemoveWishlistItemCommand;
import org.profit.candle.wishlist.wishlist.entity.WishlistItem;
import org.profit.candle.wishlist.wishlist.exception.WishlistException;
import org.profit.candle.wishlist.wishlist.repository.WishlistItemReader;
import org.profit.candle.wishlist.wishlist.repository.WishlistItemWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultWishlistServiceTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final FakeWishlistRepository repository = new FakeWishlistRepository();
    private final RecordingOutboxWriter outbox = new RecordingOutboxWriter();
    private DefaultWishlistService service;

    @BeforeEach
    void setUp() {
        service = new DefaultWishlistService(
                repository,
                repository,
                outbox,
                Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void add_createsActiveWishlistItem() {
        var result = service.add(new AddWishlistItemCommand(
                USER_ID,
                "005930",
                "삼성전자",
                "KOSPI",
                "idem-123456"
        ));

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.symbol()).isEqualTo("005930");
        assertThat(result.displayName()).isEqualTo("삼성전자");
        assertThat(repository.items).hasSize(1);
    }

    @Test
    void add_whenActiveItemExists_updatesSnapshotWithoutDuplicating() {
        service.add(new AddWishlistItemCommand(USER_ID, "005930", "삼성전자", "KOSPI", "idem-123456"));

        var result = service.add(new AddWishlistItemCommand(USER_ID, "005930", "삼전", "KOSPI", "idem-234567"));

        assertThat(result.displayName()).isEqualTo("삼전");
        assertThat(repository.items).hasSize(1);
    }

    @Test
    void remove_softDeletesActiveItem() {
        service.add(new AddWishlistItemCommand(USER_ID, "005930", "삼성전자", "KOSPI", "idem-123456"));

        service.remove(new RemoveWishlistItemCommand(USER_ID, "005930", "idem-234567"));

        assertThat(repository.items.getFirst().active()).isFalse();
    }

    @Test
    void remove_whenItemMissing_throwsWishlistException() {
        assertThatThrownBy(() -> service.remove(new RemoveWishlistItemCommand(USER_ID, "005930", "idem-123456")))
                .isInstanceOf(WishlistException.class);
    }

    @Test
    void add_firstUserForSymbol_recordsActivation() {
        service.add(new AddWishlistItemCommand(USER_ID, "005930", "삼성전자", "KOSPI", "idem-1"));

        assertThat(outbox.activated).containsExactly("005930");
    }

    @Test
    void add_reAddBySameUser_doesNotRecordActivation() {
        service.add(new AddWishlistItemCommand(USER_ID, "005930", "삼성전자", "KOSPI", "idem-1"));
        service.add(new AddWishlistItemCommand(USER_ID, "005930", "삼전", "KOSPI", "idem-2"));

        assertThat(outbox.activated).containsExactly("005930"); // 한 번만
    }

    @Test
    void add_secondUserForSameSymbol_doesNotRecordActivation() {
        service.add(new AddWishlistItemCommand(USER_ID, "005930", "삼성전자", "KOSPI", "idem-1"));
        service.add(new AddWishlistItemCommand(USER_ID_2, "005930", "삼성전자", "KOSPI", "idem-2"));

        assertThat(outbox.activated).containsExactly("005930"); // 0→1 한 번뿐, 1→2 는 이벤트 없음
    }

    @Test
    void remove_lastUserForSymbol_recordsDeactivation() {
        service.add(new AddWishlistItemCommand(USER_ID, "005930", "삼성전자", "KOSPI", "idem-1"));

        service.remove(new RemoveWishlistItemCommand(USER_ID, "005930", "idem-2"));

        assertThat(outbox.deactivated).containsExactly("005930");
    }

    @Test
    void remove_whileAnotherUserHoldsSymbol_doesNotRecordDeactivation() {
        service.add(new AddWishlistItemCommand(USER_ID, "005930", "삼성전자", "KOSPI", "idem-1"));
        service.add(new AddWishlistItemCommand(USER_ID_2, "005930", "삼성전자", "KOSPI", "idem-2"));

        service.remove(new RemoveWishlistItemCommand(USER_ID, "005930", "idem-3")); // user2 아직 보유

        assertThat(outbox.deactivated).isEmpty();
    }

    private static class RecordingOutboxWriter extends OutboxWriter {
        final List<String> activated = new ArrayList<>();
        final List<String> deactivated = new ArrayList<>();

        RecordingOutboxWriter() {
            super(null);
        }

        @Override
        public void recordSymbolActivated(String symbol) {
            activated.add(symbol);
        }

        @Override
        public void recordSymbolDeactivated(String symbol) {
            deactivated.add(symbol);
        }
    }

    private static class FakeWishlistRepository implements WishlistItemReader, WishlistItemWriter {
        private final List<WishlistItem> items = new ArrayList<>();

        @Override
        public Optional<WishlistItem> findActive(UUID userId, String symbol) {
            return items.stream()
                    .filter(WishlistItem::active)
                    .filter(item -> item.getUserId().equals(userId))
                    .filter(item -> item.getSymbol().equals(symbol))
                    .findFirst();
        }

        @Override
        public List<WishlistItem> listActive(UUID userId, int limit, int offset) {
            return items.stream()
                    .filter(WishlistItem::active)
                    .filter(item -> item.getUserId().equals(userId))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<WishlistItem> listActiveBySymbol(String symbol) {
            return items.stream()
                    .filter(WishlistItem::active)
                    .filter(item -> item.getSymbol().equals(symbol))
                    .toList();
        }

        @Override
        public WishlistItem save(WishlistItem item) {
            if (!items.contains(item)) {
                items.add(item);
            }
            return item;
        }
    }
}

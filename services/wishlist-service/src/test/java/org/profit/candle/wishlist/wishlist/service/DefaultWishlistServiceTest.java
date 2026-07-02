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
    private final FakeWishlistRepository repository = new FakeWishlistRepository();
    private DefaultWishlistService service;

    @BeforeEach
    void setUp() {
        service = new DefaultWishlistService(
                repository,
                repository,
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

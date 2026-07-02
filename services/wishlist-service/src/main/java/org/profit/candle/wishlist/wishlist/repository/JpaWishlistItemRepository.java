package org.profit.candle.wishlist.wishlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.wishlist.wishlist.entity.WishlistItem;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaWishlistItemRepository implements WishlistItemReader, WishlistItemWriter {
    private final WishlistItemJpaRepository repository;

    @Override
    public Optional<WishlistItem> findActive(UUID userId, String symbol) {
        return repository.findByUserIdAndSymbolAndDeletedAtIsNull(userId, symbol);
    }

    @Override
    public List<WishlistItem> listActive(UUID userId, int limit, int offset) {
        int page = Math.max(0, offset / Math.max(1, limit));
        return repository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId,
                PageRequest.of(page, limit)
        );
    }

    @Override
    public List<WishlistItem> listActiveBySymbol(String symbol) {
        return repository.findBySymbolAndDeletedAtIsNull(symbol);
    }

    @Override
    public WishlistItem save(WishlistItem item) {
        return repository.save(item);
    }
}

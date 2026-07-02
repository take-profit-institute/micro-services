package org.profit.candle.wishlist.wishlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.profit.candle.wishlist.wishlist.entity.WishlistItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemJpaRepository extends JpaRepository<WishlistItem, UUID> {
    Optional<WishlistItem> findByUserIdAndSymbolAndDeletedAtIsNull(UUID userId, String symbol);

    List<WishlistItem> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<WishlistItem> findBySymbolAndDeletedAtIsNull(String symbol);
}

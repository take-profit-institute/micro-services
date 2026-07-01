package org.profit.candle.wishlist.wishlist.entity;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wishlist_items")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class WishlistItem {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 20)
    private String market;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private WishlistItem(UUID userId, String symbol, String displayName, String market, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.symbol = symbol;
        this.displayName = blankToNull(displayName);
        this.market = blankToNull(market);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static WishlistItem add(UUID userId, String symbol, String displayName, String market, Instant now) {
        return new WishlistItem(userId, symbol, displayName, market, now);
    }

    public void updateSnapshot(String displayName, String market, Instant now) {
        this.displayName = blankToNull(displayName);
        this.market = blankToNull(market);
        this.updatedAt = now;
    }

    public void remove(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean active() {
        return deletedAt == null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

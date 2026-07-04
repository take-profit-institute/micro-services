package org.profit.candle.wishlist.wishlist.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.profit.candle.wishlist.wishlist.dto.AddWishlistItemCommand;
import org.profit.candle.wishlist.wishlist.dto.ListWishlistItemsResult;
import org.profit.candle.wishlist.wishlist.dto.RemoveWishlistItemCommand;
import org.profit.candle.wishlist.event.OutboxWriter;
import org.profit.candle.wishlist.wishlist.dto.WishlistItemResult;
import org.profit.candle.wishlist.wishlist.entity.WishlistItem;
import org.profit.candle.wishlist.wishlist.exception.WishlistErrorCode;
import org.profit.candle.wishlist.wishlist.exception.WishlistException;
import org.profit.candle.wishlist.wishlist.repository.WishlistItemReader;
import org.profit.candle.wishlist.wishlist.repository.WishlistItemWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultWishlistService implements WishlistService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final WishlistItemReader reader;
    private final WishlistItemWriter writer;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    @Override
    @Transactional
    public WishlistItemResult add(AddWishlistItemCommand command) {
        String symbol = normalizeSymbol(command.symbol());
        Instant now = clock.instant();
        WishlistItem existing = reader.findActive(command.userId(), symbol).orElse(null);
        if (existing != null) {
            existing.updateSnapshot(command.displayName(), command.market(), now);
            return toResult(writer.save(existing));
        }

        WishlistItem saved = writer.save(WishlistItem.add(
                command.userId(), symbol, command.displayName(), command.market(), now));
        // 이 심볼의 활성 관심 유저가 0→1 로 늘었으면 실시간 구독 수요 활성 이벤트를 발행한다.
        if (reader.listActiveBySymbol(symbol).size() == 1) {
            outboxWriter.recordSymbolActivated(symbol);
        }
        return toResult(saved);
    }

    @Override
    @Transactional
    public void remove(RemoveWishlistItemCommand command) {
        String symbol = normalizeSymbol(command.symbol());
        WishlistItem item = reader.findActive(command.userId(), symbol)
                .orElseThrow(() -> new WishlistException(WishlistErrorCode.ITEM_NOT_FOUND));
        item.remove(clock.instant());
        // 이 심볼의 활성 관심 유저가 1→0 으로 줄었으면 구독 수요 비활성 이벤트를 발행한다.
        if (reader.listActiveBySymbol(symbol).isEmpty()) {
            outboxWriter.recordSymbolDeactivated(symbol);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ListWishlistItemsResult list(UUID userId, int pageSize, String pageToken) {
        int limit = normalizePageSize(pageSize);
        int offset = decodeOffset(pageToken);
        List<WishlistItemResult> items = reader.listActive(userId, limit + 1, offset)
                .stream()
                .map(DefaultWishlistService::toResult)
                .toList();
        boolean hasNext = items.size() > limit;
        List<WishlistItemResult> page = hasNext ? items.subList(0, limit) : items;
        String nextPageToken = hasNext ? encodeOffset(offset + limit) : "";
        return new ListWishlistItemsResult(page, nextPageToken);
    }

    private static WishlistItemResult toResult(WishlistItem item) {
        return new WishlistItemResult(
                item.getId(),
                item.getUserId(),
                item.getSymbol(),
                item.getDisplayName(),
                item.getMarket(),
                item.getCreatedAt()
        );
    }

    private static int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static int decodeOffset(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(pageToken));
            return Math.max(0, Integer.parseInt(decoded));
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static String encodeOffset(int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(offset).getBytes());
    }

    public static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank() || symbol.length() > 20) {
            throw new WishlistException(WishlistErrorCode.INVALID_SYMBOL);
        }
        return symbol.trim().toUpperCase();
    }
}

package org.profit.candle.portfolio.holding.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.portfolio.holding.dto.ActiveHolderResult;
import org.profit.candle.portfolio.holding.dto.HoldingResult;
import org.profit.candle.portfolio.holding.dto.ListActiveHoldersResult;
import org.profit.candle.portfolio.holding.dto.PositionResult;
import org.profit.candle.portfolio.holding.entity.HoldingEntity;
import org.profit.candle.portfolio.holding.entity.SellOutcome;
import org.profit.candle.portfolio.holding.exception.HoldingErrorCode;
import org.profit.candle.portfolio.holding.exception.HoldingException;
import org.profit.candle.portfolio.holding.repository.HoldingReader;
import org.profit.candle.portfolio.holding.repository.HoldingWriter;
import org.profit.candle.portfolio.holding.trade.entity.RealizedTradeEntity;
import org.profit.candle.portfolio.holding.trade.repository.RealizedTradeWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultHoldingService implements HoldingService {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;

    private final HoldingReader holdingReader;
    private final HoldingWriter holdingWriter;
    private final RealizedTradeWriter realizedTradeWriter;

    @Override
    @Transactional(readOnly = true)
    public List<HoldingResult> listHoldings(String userId, boolean includeInactive) {
        List<HoldingEntity> holdings = includeInactive
                ? holdingReader.findByUserId(userId)
                : holdingReader.findActiveByUserId(userId);
        return holdings.stream().map(HoldingResult::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HoldingResult getHolding(String userId, String symbol) {
        return holdingReader.findByUserIdAndSymbol(userId, symbol)
                .filter(HoldingEntity::active)
                .map(HoldingResult::from)
                .orElseThrow(() -> new HoldingException(HoldingErrorCode.HOLDING_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public ListActiveHoldersResult listActiveHolders(int pageSize, String pageToken) {
        int normalizedPageSize = normalizePageSize(pageSize);
        String lastUserId = blankToNull(pageToken);

        // 1단계: 활성 유저 id 를 user_id ASC keyset 으로 pageSize+1 개 조회 → hasNext 판정.
        List<String> userIds = holdingReader.findActiveUserIdsAfter(lastUserId, normalizedPageSize + 1);
        boolean hasNext = userIds.size() > normalizedPageSize;
        List<String> pageUserIds = hasNext ? userIds.subList(0, normalizedPageSize) : userIds;

        if (pageUserIds.isEmpty()) {
            return new ListActiveHoldersResult(List.of(), "");
        }

        // 2단계: 해당 유저들의 활성 보유종목을 (user_id, symbol) ASC 로 모아 유저 단위로 그룹핑.
        List<HoldingEntity> holdings = holdingReader.findActiveHoldingsByUserIds(pageUserIds);
        Map<String, List<PositionResult>> byUser = new LinkedHashMap<>();
        for (String userId : pageUserIds) {
            byUser.put(userId, new ArrayList<>());
        }
        for (HoldingEntity h : holdings) {
            byUser.get(h.userId()).add(PositionResult.from(h));
        }

        List<ActiveHolderResult> holders = byUser.entrySet().stream()
                .map(e -> new ActiveHolderResult(e.getKey(), e.getValue()))
                .toList();

        String nextPageToken = hasNext ? pageUserIds.get(pageUserIds.size() - 1) : "";
        return new ListActiveHoldersResult(holders, nextPageToken);
    }

    @Override
    @Transactional
    public void applyBuyFill(String userId, String symbol, long quantity, long executedPrice) {
        HoldingEntity holding = holdingReader.findByUserIdAndSymbol(userId, symbol)
                .orElseGet(() -> new HoldingEntity(userId, symbol, "", "", ""));
        holding.applyBuy(quantity, executedPrice);
        holdingWriter.save(holding);
    }

    @Override
    @Transactional
    public void applySellFill(String userId, String symbol, long quantity, long executedPrice) {
        HoldingEntity holding = holdingReader.findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(() -> new HoldingException(HoldingErrorCode.HOLDING_NOT_FOUND));
        SellOutcome outcome = holding.applySell(quantity, executedPrice);
        holdingWriter.save(holding);
        realizedTradeWriter.save(new RealizedTradeEntity(userId, symbol, outcome));
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize == 0) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page_size must be between 1 and 500");
        }
        return pageSize;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

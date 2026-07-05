package org.profit.candle.portfolio.holding.service;

import org.profit.candle.portfolio.holding.dto.HoldingResult;
import org.profit.candle.portfolio.holding.dto.ListActiveHoldersResult;

import java.util.List;

public interface HoldingService {
    List<HoldingResult> listHoldings(String userId, boolean includeInactive);
    HoldingResult getHolding(String userId, String symbol);
    ListActiveHoldersResult listActiveHolders(int pageSize, String pageToken);
    void applyBuyFill(String userId, String symbol, long quantity, long executedPrice);
    void applySellFill(String userId, String symbol, long quantity, long executedPrice);
}

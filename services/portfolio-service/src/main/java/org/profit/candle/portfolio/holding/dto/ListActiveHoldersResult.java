package org.profit.candle.portfolio.holding.dto;

import java.util.List;

public record ListActiveHoldersResult(
        List<ActiveHolderResult> holders,
        String nextPageToken
) {
}

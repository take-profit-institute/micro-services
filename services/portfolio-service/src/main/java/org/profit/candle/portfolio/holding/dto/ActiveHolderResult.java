package org.profit.candle.portfolio.holding.dto;

import java.util.List;

public record ActiveHolderResult(
        String userId,
        List<PositionResult> positions
) {
}

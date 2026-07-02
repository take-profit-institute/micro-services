package org.profit.candle.ranking.ranking.client;

import java.util.List;

public record PortfolioSnapshotPage(
        List<PortfolioSnapshotItem> items,
        String nextPageToken) {

    /** 외부에서 전달된 목록이 변경되지 않도록 복사한다. */
    public PortfolioSnapshotPage {
        items = List.copyOf(items);
    }

    /** 다음 페이지가 존재하는지 반환한다. */
    public boolean hasNext() {
        return nextPageToken != null && !nextPageToken.isBlank();
    }
}

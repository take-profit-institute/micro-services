package org.profit.candle.portfolio.analytics.market;

import java.util.Collection;
import java.util.Map;

public interface MarketQuoteClient {
    Map<String, Long> currentPrices(Collection<String> symbols);
}

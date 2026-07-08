package org.profit.candle.market.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.profit.candle.market.dto.IntradayTickResult;

public interface IntradayTickCache {

    Optional<List<IntradayTickResult>> get(String symbol);

    void put(String symbol, List<IntradayTickResult> ticks, Duration ttl);
}

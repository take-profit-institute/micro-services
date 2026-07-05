package org.profit.candle.batch.trading.client;

import java.time.LocalDate;

public interface DailyCandleCloseClient {

    int close(LocalDate tradeDate, String idempotencyKey);
}

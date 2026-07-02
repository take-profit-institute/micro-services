package org.profit.candle.batch.portfolio.eod.client;

import java.time.LocalDate;
import java.util.List;
import org.profit.candle.batch.portfolio.eod.model.ClosingPrice;

public interface ClosingPriceClient {

    List<ClosingPrice> loadClosingPrices(LocalDate businessDate, List<String> symbols);
}

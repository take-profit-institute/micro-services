package org.profit.candle.stock.chart.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.entity.CandleEntity;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.profit.candle.stock.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultDailyCloseService implements DailyCloseService {

    private final CandleReader candleReader;
    private final OutboxWriter outboxWriter;

    @Override
    @Transactional
    public int closeDaily(LocalDate tradeDate) {
        // 일봉 open_time 은 거래일의 UTC 자정으로 저장된다(키움 dt 파싱과 동일 규약).
        Instant openTime = tradeDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<CandleEntity> open = candleReader.findOpenAt(CandleInterval.DAY_1.storageValue(), openTime);
        for (CandleEntity candle : open) {
            candle.markClosed(); // 같은 트랜잭션의 dirty checking 으로 반영
            outboxWriter.recordStockDailyClosed(candle.id().stockCode(), tradeDate, candle.close());
        }
        return open.size();
    }
}

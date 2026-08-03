package org.profit.candle.stock.chart.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.chart.dto.CandleInterval;
import org.profit.candle.stock.chart.repository.CandleClose;
import org.profit.candle.stock.chart.repository.CandleReader;
import org.profit.candle.stock.chart.repository.CandleWriter;
import org.profit.candle.stock.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultDailyCloseService implements DailyCloseService {

    private final CandleReader candleReader;
    private final CandleWriter candleWriter;
    private final OutboxWriter outboxWriter;

    @Override
    @Transactional
    public int closeDaily(LocalDate tradeDate) {
        // 일봉 open_time 은 거래일의 UTC 자정으로 저장된다(키움 dt 파싱과 동일 규약).
        Instant openTime = tradeDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        String interval = CandleInterval.DAY_1.storageValue();

        // 1) 아직 확정되지 않은 캔들을 한 번에 마감 처리한다.
        candleWriter.markClosedAt(interval, openTime);

        // 2) 이벤트 발행 대상은 closed 여부와 무관하게 그날 일봉 전체다.
        //    적재 배치가 정규장 마감(15:30 KST) 이후에 돌면 CandleInterval.isPeriodClosed 가 true 라
        //    캔들이 애초에 closed=true 로 들어온다. 따라서 closed 플래그를 발행 조건으로 쓰면
        //    발행 대상이 0건이 된다(실제로 그랬다). 중복은 outbox 의 결정적 eventId 가 막는다.
        List<CandleClose> closes = candleReader.findClosesAt(interval, openTime);
        int recorded = 0;
        for (CandleClose candle : closes) {
            if (outboxWriter.recordStockDailyClosed(candle.stockCode(), tradeDate, candle.close())) {
                recorded++;
            }
        }
        return recorded;
    }
}

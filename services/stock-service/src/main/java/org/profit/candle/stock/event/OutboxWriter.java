package org.profit.candle.stock.event;

import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.event.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 도메인 변경과 같은 트랜잭션에서 outbox 행을 기록한다(at-least-once). */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 종가 이벤트를 outbox 에 기록한다. eventId 가 (종목, 거래일)에서 결정적으로 나오므로
     * 같은 거래일을 다시 마감 처리해도 중복 행이 생기지 않는다.
     *
     * @return 이번 호출에서 새로 기록했으면 true, 이미 있었으면 false
     */
    public boolean recordStockDailyClosed(String stockCode, LocalDate tradeDate, long close) {
        Instant now = Instant.now();
        StockDailyClosedEvent event = StockDailyClosedEvent.create(stockCode, tradeDate, close, now);
        return outboxEventRepository.insertIfAbsent(
                event.eventId(),
                event.eventType(),
                stockCode,
                objectMapper.writeValueAsString(event),
                now) > 0;
    }
}

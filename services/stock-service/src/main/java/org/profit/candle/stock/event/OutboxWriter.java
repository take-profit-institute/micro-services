package org.profit.candle.stock.event;

import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.profit.candle.stock.event.entity.OutboxEvent;
import org.profit.candle.stock.event.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 도메인 변경과 같은 트랜잭션에서 outbox 행을 기록한다(at-least-once). */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void recordStockDailyClosed(String stockCode, LocalDate tradeDate, long close) {
        Instant now = Instant.now();
        StockDailyClosedEvent event = StockDailyClosedEvent.create(stockCode, tradeDate, close, now);
        outboxEventRepository.save(new OutboxEvent(event.eventId(), event.eventType(), stockCode,
                objectMapper.writeValueAsString(event), now));
    }
}

package org.profit.candle.portfolio.holding.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.portfolio.holding.event.dto.OrderFilledPayload;
import org.profit.candle.portfolio.holding.event.entity.ConsumedEvent;
import org.profit.candle.portfolio.holding.event.repository.ConsumedEventRepository;
import org.profit.candle.portfolio.holding.service.HoldingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingEventConsumer {

    private final HoldingService holdingService;
    private final ConsumedEventRepository consumedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = ConsumedTopics.TRADING_ORDER_FILLED)
    @Transactional
    public void onOrderFilled(String rawPayload) {
        OrderFilledPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, OrderFilledPayload.class);
        } catch (Exception e) {
            log.error("OrderFilled 이벤트 역직렬화 실패. payload={}", rawPayload, e);
            return;
        }

        // 멱등 키 = orderId (주문당 체결 1회). 발행측(trading) 계약에 eventId가 없으므로 orderId로 dedup.
        final UUID dedupId;
        try {
            dedupId = UUID.fromString(payload.orderId());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.error("OrderFilled orderId 형식 오류 skip. payload={}", rawPayload, e);
            return;
        }

        if (consumedEventRepository.existsById(dedupId)) {
            log.info("이미 처리된 이벤트 skip. orderId={}", payload.orderId());
            return;
        }

        try {
            switch (payload.side().toUpperCase()) {
                case "BUY" -> holdingService.applyBuyFill(
                        payload.userId(), payload.symbol(), payload.executedQuantity(), payload.executedPriceKrw());
                case "SELL" -> holdingService.applySellFill(
                        payload.userId(), payload.symbol(), payload.executedQuantity(), payload.executedPriceKrw());
                default -> {
                    log.warn("알 수 없는 주문 방향. side={}, orderId={}", payload.side(), payload.orderId());
                    return;
                }
            }
            consumedEventRepository.save(new ConsumedEvent(dedupId, "OrderFilled"));
            log.info("보유종목 업데이트 완료. userId={}, symbol={}, side={}",
                    payload.userId(), payload.symbol(), payload.side());
        } catch (Exception e) {
            log.error("보유종목 업데이트 실패. orderId={}", payload.orderId(), e);
            throw e;
        }
    }
}

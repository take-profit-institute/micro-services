package org.profit.candle.ranking.ranking.event;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.ranking.ranking.service.RankingParticipantProjectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFilledConsumer {

    private final ObjectMapper objectMapper;
    private final RankingParticipantProjectionService projectionService;

    /** 일반·예약 주문의 OrderFilled를 검증한 뒤 누적 거래 횟수 투영으로 전달한다. */
    @KafkaListener(topics = "${ranking.kafka.order-filled-topic}")
    public void onOrderFilled(String rawPayload) {
        OrderFilledEvent event;
        try {
            event = objectMapper.readValue(rawPayload, OrderFilledEvent.class);
        } catch (Exception exception) {
            log.warn("OrderFilled event could not be deserialized");
            return;
        }

        if (!valid(event)) {
            log.warn("OrderFilled event is invalid");
            return;
        }
        projectionService.projectFilledOrder(event);
    }

    /** 현재 Trading 계약에서 멱등 키와 참가자 식별자에 필요한 필드를 검사한다. */
    private boolean valid(OrderFilledEvent event) {
        if (event.orderId() == null || event.orderId().isBlank()
                || event.userId() == null || event.userId().isBlank()
                || event.executedQuantity() <= 0) {
            return false;
        }
        try {
            UUID.fromString(event.orderId());
            UUID.fromString(event.userId());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}

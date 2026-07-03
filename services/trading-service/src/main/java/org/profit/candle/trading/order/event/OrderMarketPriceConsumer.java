package org.profit.candle.trading.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.profit.candle.trading.order.service.CachedMarketPriceProvider;
import org.profit.candle.trading.order.service.OrderExecutionService;
import org.profit.candle.trading.support.event.MarketPriceEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Market 도메인의 현재가 Kafka 이벤트를 구독해:
 * 1. CachedMarketPriceProvider 캐시를 갱신한다 (시장가 즉시 체결용 EXE-001)
 * 2. 해당 종목의 PENDING 지정가 주문 중 조건 충족한 것들을 체결한다 (EXE-002)
 *
 * <p>토픽명({@code TOPIC})은 Market 담당자(팀장)와 협의 후 확정 예정.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMarketPriceConsumer {

    private static final String TOPIC = "market.open-price.v1";
    private static final String GROUP_ID = "trading-service-order-market-price";

    private final CachedMarketPriceProvider cachedMarketPriceProvider;
    private final OrderExecutionService orderExecutionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(ConsumerRecord<String, String> record) {
        MarketPriceEvent event;
        try {
            event = objectMapper.readValue(record.value(), MarketPriceEvent.class);
        } catch (Exception e) {
            // poison pill — 재시도해도 동일하게 실패하므로 skip. PII 포함 가능성으로 value 로그 제외.
            log.error("현재가 이벤트 역직렬화 실패 — offset={}, topic={}",
                    record.offset(), record.topic(), e);
            return;
        }

        try {
            if (event.symbol() == null || event.symbol().isBlank()) {
                log.error("현재가 이벤트 symbol 누락 — offset={}", record.offset());
                return;
            }
            if (event.price() <= 0) {
                log.error("현재가 이벤트 유효하지 않은 price — symbol={}, price={}, offset={}",
                        event.symbol(), event.price(), record.offset());
                return;
            }

            // 1. 캐시 갱신 — 시장가 즉시 체결(EXE-001)에서 getCurrentPriceKrw()가 읽는 값
            cachedMarketPriceProvider.updatePrice(event.symbol(), event.price());

            // 2. 지정가 조건 체결(EXE-002) — 방금 갱신된 가격으로 PENDING 지정가 주문 스캔
            int count = orderExecutionService.fillLimitOrdersIfConditionMet(
                    event.symbol(), event.price());

            if (count > 0) {
                log.info("지정가 조건 체결 완료 — symbol={}, price={}, count={}",
                        event.symbol(), event.price(), count);
            }
        } catch (Exception e) {
            // 처리 실패 — 재throw로 오프셋 커밋 차단, Kafka 재시도 유도
            log.error("현재가 이벤트 처리 실패 — symbol={}, offset={}",
                    event.symbol(), record.offset(), e);
            throw new RuntimeException("현재가 이벤트 처리 실패 — symbol: " + event.symbol(), e);
        }
    }
}
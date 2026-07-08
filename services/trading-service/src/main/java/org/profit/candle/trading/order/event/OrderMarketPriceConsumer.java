package org.profit.candle.trading.order.event;

import tools.jackson.databind.ObjectMapper;
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
 *
 * <p><b>[2026-07-08 현황]</b> market-service는 현재 Kafka를 전혀 발행하지 않고
 * Redis Pub/Sub({@code market:quotes} 채널)만 사용한다. 즉 이 리스너는 지금
 * 실제로는 아무 메시지도 수신하지 못하는 상태다 — 같은 역할(캐시 갱신 + 지정가 체결)은
 * {@code trading.support.event.MarketQuoteRedisSubscriber}가 Redis 경로로 대신 수행 중이다.
 *
 * <p>이 클래스는 삭제하지 않고 유지한다 — 팀장 지시로, market-service가 추후 Kafka 발행을
 * 추가하는 경우를 대비해 토픽명(TOPIC 상수)만 교체하면 바로 살아날 수 있게 남겨둔다.
 * 두 경로(Kafka/Redis)가 동시에 활성화되어도 아래 처리(캐시 갱신, 지정가 조건 체결)는
 * 멱등하므로 중복 실행 자체가 정합성 문제를 일으키지는 않는다 — 다만 실제로 market-service가
 * Kafka 발행을 붙이는 시점에는 두 경로를 동시에 둘지, 하나로 정리할지 다시 논의 필요.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMarketPriceConsumer {

    private static final String TOPIC = "market.order-book.v1";
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
package org.profit.candle.trading.support.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.trading.account.repository.AccountOutboxEventRepository;
import org.profit.candle.trading.order.repository.OrderOutboxEventRepository;
import org.profit.candle.trading.reservation.repository.ReservationOutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * trading 아웃박스(order/reservation/account 3개 스키마)의 미발행 이벤트를 Kafka로 직접 발행한다.
 *
 * <p>인프라에 CDC(Debezium)가 도입되기 전까지 쓰는 임시 릴레이다. CDC로 넘어가면 이 클래스를
 * 제거하고 커넥터가 같은 토픽으로 발행하면 된다.</p>
 *
 * <p>토픽 규칙: 기본은 {@code <aggregate-prefix>.<EventType>}. 단 소비 계약이 이미 정해진
 * 이벤트는 그 토픽을 그대로 쓴다(예: {@code OrderFilled} → {@code orderFilled}, portfolio 소비).
 * key=aggregateId로 애그리거트별 partition 순서를 유지한다. 전송 성공 시에만 published_at을
 * 기록하므로 at-least-once이며, 소비자는 멱등 처리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingKafkaOutboxPublisher {

    private final OrderOutboxEventRepository orderOutboxRepository;
    private final ReservationOutboxEventRepository reservationOutboxRepository;
    private final AccountOutboxEventRepository accountOutboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${trading.outbox.publish-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        orderOutboxRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc().forEach(e -> {
            kafkaTemplate.send(TradingOutboxTopics.forOrderEvent(e.eventType()), e.aggregateId(), e.payload()).join();
            e.markPublished(Instant.now());
        });
        reservationOutboxRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc().forEach(e -> {
            kafkaTemplate.send(TradingOutboxTopics.forReservationEvent(e.eventType()), e.aggregateId(), e.payload()).join();
            e.markPublished(Instant.now());
        });
        accountOutboxRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc().forEach(e -> {
            kafkaTemplate.send(TradingOutboxTopics.forAccountEvent(e.eventType()), e.aggregateId(), e.payload()).join();
            e.markPublished(Instant.now());
        });
    }
}

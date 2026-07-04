package org.profit.candle.trading.reservation.event;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.service.ReservationBatchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * order_svc가 ReservationDue 처리 후 발행한 ReservationConverted 이벤트를 수신해
 * CONVERTING → EXECUTED로 전이한다 (Option C: 전체 비동기 Kafka).
 *
 * <p>토픽명은 order_svc의 Outbox CDC 토픽과 맞춰야 한다 — Debezium 설정 확인 필요.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationConvertedConsumer {

    // TODO: Debezium CDC 토픽명 확정 후 교체
    private static final String TOPIC = "trading.order.ReservationConverted";
    private static final String GROUP_ID = "trading-service-reservation-converted";

    private final ReservationBatchService reservationBatchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(ConsumerRecord<String, String> record) {
        ReservationConvertedPayload event;
        try {
            event = objectMapper.readValue(record.value(), ReservationConvertedPayload.class);
        } catch (Exception e) {
            // poison pill — 재시도해도 동일하게 실패하므로 skip.
            log.error("ReservationConverted 역직렬화 실패 — offset={}, topic={}",
                    record.offset(), record.topic(), e);
            return;
        }

        try {
            reservationBatchService.markConverted(
                    UUID.fromString(event.reservationId()),
                    UUID.fromString(event.orderId()));

            log.info("ReservationConverted 처리 완료 — reservationId={}, orderId={}",
                    event.reservationId(), event.orderId());
        } catch (ReservationException | IllegalArgumentException e) {
            // 이미 EXECUTED/CANCELLED 등 상태 전이 불가, 또는 UUID 파싱 실패 —
            // 재시도해도 동일하게 실패하므로 skip (poison pill 방지).
            log.warn("ReservationConverted skip — reservationId={}, reason={}",
                    event.reservationId(), e.getMessage());
        } catch (Exception e) {
            // 일시적 오류(DB 장애 등) — 재throw로 오프셋 커밋 차단, Kafka 재시도 유도.
            log.error("ReservationConverted 처리 실패 — reservationId={}, offset={}",
                    event.reservationId(), record.offset(), e);
            throw new RuntimeException("ReservationConverted 처리 실패 — reservationId: "
                    + event.reservationId(), e);
        }
    }
}
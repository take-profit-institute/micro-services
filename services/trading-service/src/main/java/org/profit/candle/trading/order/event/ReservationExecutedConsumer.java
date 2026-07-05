package org.profit.candle.trading.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.service.OrderService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * reservation_svc가 발행한 ReservationExecuted 이벤트를 수신해, 확정 체결가로 즉시 체결된
 * Order를 생성한다(시장가/종가 예약). OPEN+LIMIT의 {@link ReservationDueConsumer}와 대칭이며,
 * 이 경로로 예약 체결이 OrderFilled → portfolio 보유종목까지 반영된다.
 *
 * <p>멱등: {@link OrderService#recordReservationFill}이 reservationId 기반 idempotencyKey로
 * 재수신을 흡수한다. unique 위반(동시 재전송)도 이미 처리된 건으로 간주하고 skip한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExecutedConsumer {

    // TODO: Debezium CDC 토픽명 확정 후 교체
    private static final String TOPIC = "trading.reservation.ReservationExecuted";
    private static final String GROUP_ID = "trading-service-order-reservation-executed";

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(ConsumerRecord<String, String> record) {
        ReservationExecutedPayload event;
        try {
            event = objectMapper.readValue(record.value(), ReservationExecutedPayload.class);
        } catch (Exception e) {
            // poison pill — 재시도해도 동일하게 실패하므로 skip.
            log.error("ReservationExecuted 역직렬화 실패 — offset={}, topic={}",
                    record.offset(), record.topic(), e);
            return;
        }

        try {
            orderService.recordReservationFill(
                    UUID.fromString(event.userId()),
                    event.symbol(),
                    OrderSideValue.valueOf(event.side()),
                    event.quantity(),
                    event.executedPrice(),
                    event.reservedAmount(),
                    UUID.fromString(event.reservationId()));

            log.info("ReservationExecuted 처리 완료 — reservationId={}", event.reservationId());

        } catch (IllegalArgumentException e) {
            // UUID/enum 파싱 실패 등 poison pill — 재시도해도 동일하므로 skip.
            log.error("ReservationExecuted poison pill skip — reservationId={}, reason={}",
                    event.reservationId(), e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // idempotencyKey unique 위반 — 이미 처리된 건으로 간주, skip.
            log.warn("ReservationExecuted skip (이미 처리됨) — reservationId={}", event.reservationId());
        } catch (Exception e) {
            // 일시적 오류 — 재throw로 오프셋 커밋 차단, Kafka 재시도 유도.
            log.error("ReservationExecuted 처리 실패 — reservationId={}, offset={}",
                    event.reservationId(), record.offset(), e);
            throw new RuntimeException("ReservationExecuted 처리 실패 — reservationId: "
                    + event.reservationId(), e);
        }
    }
}

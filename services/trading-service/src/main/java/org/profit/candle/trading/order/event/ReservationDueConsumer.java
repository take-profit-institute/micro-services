package org.profit.candle.trading.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.profit.candle.trading.order.service.OrderService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * reservation_svc가 발행한 ReservationDue 이벤트를 수신해 Order를 생성한다.
 *
 * <p>Order 생성과 ReservationConverted Outbox 기록은 {@link OrderService#placeOrderFromReservation}
 * 안에서 한 트랜잭션으로 처리된다 — 원자성 보장 (Qodo #4).</p>
 *
 * <p>멱등 처리: DUPLICATE_PENDING_ORDER / DataIntegrityViolationException 발생 시
 * 이미 처리된 건으로 간주하고 skip한다 (Qodo #5).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationDueConsumer {

    // TODO: Debezium CDC 토픽명 확정 후 교체
    private static final String TOPIC = "trading.reservation.ReservationDue";
    private static final String GROUP_ID = "trading-service-order-reservation-due";

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(ConsumerRecord<String, String> record) {
        ReservationDuePayload event;
        try {
            event = objectMapper.readValue(record.value(), ReservationDuePayload.class);
        } catch (Exception e) {
            // poison pill — 재시도해도 동일하게 실패하므로 skip.
            log.error("ReservationDue 역직렬화 실패 — offset={}, topic={}",
                    record.offset(), record.topic(), e);
            return;
        }

        try {
            UUID userId = UUID.fromString(event.userId());
            UUID reservationId = UUID.fromString(event.reservationId());

            PlaceOrderCommand command = new PlaceOrderCommand(
                    event.symbol(),
                    OrderSideValue.valueOf(event.side()),
                    OrderKindValue.LIMIT,
                    event.quantity(),
                    event.priceKrw(),
                    event.idempotencyKey());

            orderService.placeOrderFromReservation(userId, command, event.reservedAmountKrw(), reservationId);

            log.info("ReservationDue 처리 완료 — reservationId={}", reservationId);

        } catch (IllegalArgumentException e) {
            // UUID 파싱 실패, enum valueOf 실패 등 poison pill — 재시도해도 동일하므로 skip (Qodo #3).
            log.error("ReservationDue poison pill skip — reservationId={}, reason={}",
                    event.reservationId(), e.getMessage());
        } catch (OrderException e) {
            if (e.errorCode() == OrderErrorCode.DUPLICATE_PENDING_ORDER) {
                // 이미 처리된 건 — skip (Qodo #5)
                log.warn("ReservationDue skip (이미 처리됨) — reservationId={}", event.reservationId());
            } else {
                // 그 외 비즈니스 실패 — 재throw로 Kafka 재시도 유도 (Qodo #1)
                log.error("ReservationDue 비즈니스 실패 — reservationId={}, errorCode={}",
                        event.reservationId(), e.errorCode());
                throw new RuntimeException("ReservationDue 처리 실패 — reservationId: "
                        + event.reservationId(), e);
            }
        } catch (DataIntegrityViolationException e) {
            // unique 위반 — 이미 처리된 건으로 간주, skip
            log.warn("ReservationDue skip (unique 위반) — reservationId={}", event.reservationId());
        } catch (Exception e) {
            // 일시적 오류 — 재throw로 오프셋 커밋 차단, Kafka 재시도 유도
            log.error("ReservationDue 처리 실패 — reservationId={}, offset={}",
                    event.reservationId(), record.offset(), e);
            throw new RuntimeException("ReservationDue 처리 실패 — reservationId: "
                    + event.reservationId(), e);
        }
    }
}
package org.profit.candle.trading.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.service.OrderService;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * reservation_svc가 발행한 ReservationDue 이벤트를 수신해 Order를 생성한다.
 *
 * <p>처리 완료 후 ReservationConverted 이벤트를 Outbox에 기록한다 —
 * reservation_svc 컨슈머({@link org.profit.candle.trading.reservation.event.ReservationConvertedConsumer})가
 * 수신해 CONVERTING → EXECUTED로 전이한다 (Option C: 전체 비동기 Kafka).</p>
 *
 * <p>토픽명은 reservation_svc의 Outbox CDC 토픽과 맞춰야 한다 — Debezium 설정 확인 필요.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationDueConsumer {

    // TODO: Debezium CDC 토픽명 확정 후 교체
    private static final String TOPIC = "trading.reservation.ReservationDue";
    private static final String GROUP_ID = "trading-service-order-reservation-due";

    private final OrderService orderService;
    private final OutboxWriter outboxWriter;
    private final OrderOutboxOperations outboxOperations;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(ConsumerRecord<String, String> record) {
        ReservationDuePayload event;
        try {
            event = objectMapper.readValue(record.value(), ReservationDuePayload.class);
        } catch (Exception e) {
            // poison pill — 재시도해도 동일하게 실패하므로 skip. PII 포함 가능성으로 value 로그 제외.
            log.error("ReservationDue 역직렬화 실패 — offset={}, topic={}",
                    record.offset(), record.topic(), e);
            return;
        }

        try {
            UUID userId = UUID.fromString(event.userId());
            UUID reservationId = UUID.fromString(event.reservationId());

            // OPEN+LIMIT: 지정가 주문 생성.
            // placeOrderFromReservation()은 placeOrder()와 달리:
            //   - tradingHoursValidator 검증 없음 (배치 트리거라 시간 무관)
            //   - lockBalance 없음 (예약 생성 시점에 이미 잠금됨)
            //   - fillMarketOrder 없음 (지정가라 즉시 체결 불필요)
            PlaceOrderCommand command = new PlaceOrderCommand(
                    event.symbol(),
                    OrderSideValue.valueOf(event.side()),
                    OrderKindValue.LIMIT,
                    event.quantity(),
                    event.priceKrw() == null ? 0 : event.priceKrw(),
                    event.idempotencyKey());

            OrderEntity order = orderService.placeOrderFromReservation(userId, command);

            // ReservationConverted 이벤트 발행 — reservation_svc가 수신해 markConverted() 호출
            outboxWriter.record(outboxOperations, "ReservationConverted",
                    reservationId.toString(),
                    new ReservationConvertedPayload(
                            reservationId.toString(),
                            order.getId().toString(),
                            userId.toString()));

            log.info("ReservationDue 처리 완료 — reservationId={}, orderId={}",
                    reservationId, order.getId());
        } catch (Exception e) {
            // 처리 실패 — 재throw로 오프셋 커밋 차단, Kafka 재시도 유도
            log.error("ReservationDue 처리 실패 — reservationId={}, offset={}",
                    event.reservationId(), record.offset(), e);
            throw new RuntimeException("ReservationDue 처리 실패 — reservationId: "
                    + event.reservationId(), e);
        }
    }
}
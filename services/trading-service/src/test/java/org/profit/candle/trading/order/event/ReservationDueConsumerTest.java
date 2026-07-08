package org.profit.candle.trading.order.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.profit.candle.trading.order.service.OrderService;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ReservationDueConsumer 테스트.
 * 실제 역직렬화 동작을 검증하기 위해 ObjectMapper는 mock이 아닌 실제 인스턴스를 사용한다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationDueConsumerTest {

    @Mock private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ReservationDueConsumer consumer;

    private final UUID reservationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new ReservationDueConsumer(orderService, objectMapper);
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("trading.reservation.ReservationDue", 0, 0L, reservationId.toString(), json);
    }

    private String validPayloadJson() {
        return """
                {"reservationId":"%s","userId":"%s","accountId":"%s","symbol":"005930",
                 "side":"BUY","quantity":10,"priceKrw":70000,"reservedAmountKrw":700105,
                 "idempotencyKey":"reservation-idem-1"}
                """.formatted(reservationId, userId, UUID.randomUUID());
    }

    @Test
    @DisplayName("정상 페이로드를 PlaceOrderCommand로 매핑해 placeOrderFromReservation을 호출한다")
    void shouldCallPlaceOrderFromReservationWithMappedCommand() {
        consumer.consume(record(validPayloadJson()));

        ArgumentCaptor<PlaceOrderCommand> commandCaptor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
        verify(orderService).placeOrderFromReservation(eq(userId), commandCaptor.capture(),
                eq(700_105L), eq(reservationId));

        PlaceOrderCommand command = commandCaptor.getValue();
        assertThat(command.symbol()).isEqualTo("005930");
        assertThat(command.side()).isEqualTo(OrderSideValue.BUY);
        assertThat(command.kind()).isEqualTo(OrderKindValue.LIMIT); // ReservationDue는 항상 LIMIT으로 매핑
        assertThat(command.quantity()).isEqualTo(10);
        assertThat(command.price()).isEqualTo(70_000L);
    }

    @Test
    @DisplayName("역직렬화 실패(poison pill) 시 예외 없이 조용히 skip한다")
    void shouldSkipSilentlyOnDeserializationFailure() {
        assertThatCode(() -> consumer.consume(record("not-a-json")))
                .doesNotThrowAnyException();

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("DUPLICATE_PENDING_ORDER는 이미 처리된 건으로 간주해 skip한다")
    void shouldSkipSilentlyOnDuplicatePendingOrder() {
        doThrow(new OrderException(OrderErrorCode.DUPLICATE_PENDING_ORDER))
                .when(orderService).placeOrderFromReservation(any(), any(), anyLong(), any());

        assertThatCode(() -> consumer.consume(record(validPayloadJson())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DUPLICATE_PENDING_ORDER 외의 업무 예외는 RuntimeException으로 감싸 재throw해 재시도를 유도한다")
    void shouldRethrowOnOtherOrderException() {
        doThrow(new OrderException(OrderErrorCode.OUTSIDE_TRADING_HOURS))
                .when(orderService).placeOrderFromReservation(any(), any(), anyLong(), any());

        assertThatThrownBy(() -> consumer.consume(record(validPayloadJson())))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(OrderException.class);
    }

    @Test
    @DisplayName("unique 제약 위반(DataIntegrityViolationException)은 이미 처리된 건으로 간주해 skip한다")
    void shouldSkipOnDataIntegrityViolationException() {
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(orderService).placeOrderFromReservation(any(), any(), anyLong(), any());

        assertThatCode(() -> consumer.consume(record(validPayloadJson())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("side 값이 OrderSideValue enum에 없는 값이면 poison pill로 간주해 skip한다")
    void shouldSkipOnInvalidSideEnumValue() {
        String invalidSideJson = """
                {"reservationId":"%s","userId":"%s","accountId":"%s","symbol":"005930",
                 "side":"HOLD","quantity":10,"priceKrw":70000,"reservedAmountKrw":700105,
                 "idempotencyKey":"reservation-idem-2"}
                """.formatted(reservationId, userId, UUID.randomUUID());

        assertThatCode(() -> consumer.consume(record(invalidSideJson)))
                .doesNotThrowAnyException();

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("예상치 못한 일시적 오류는 RuntimeException으로 감싸 재throw해 오프셋 커밋을 막는다")
    void shouldRethrowOnUnexpectedException() {
        doThrow(new RuntimeException("DB connection lost"))
                .when(orderService).placeOrderFromReservation(any(), any(), anyLong(), any());

        assertThatThrownBy(() -> consumer.consume(record(validPayloadJson())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ReservationDue 처리 실패");
    }
}

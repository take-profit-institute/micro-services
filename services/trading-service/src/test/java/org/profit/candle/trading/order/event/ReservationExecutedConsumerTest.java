package org.profit.candle.trading.order.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.service.OrderService;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationExecutedConsumerTest {

    @Mock private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ReservationExecutedConsumer consumer;

    private final UUID reservationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new ReservationExecutedConsumer(orderService, objectMapper);
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("trading.reservation.ReservationExecuted", 0, 0L,
                reservationId.toString(), json);
    }

    private String validPayloadJson() {
        return """
                {"reservationId":"%s","userId":"%s","accountId":"%s","symbol":"005930",
                 "side":"SELL","quantity":10,"executedPrice":70000,"reservedAmount":0}
                """.formatted(reservationId, userId, UUID.randomUUID());
    }

    @Test
    @DisplayName("정상 페이로드를 파싱해 recordReservationFill을 호출한다")
    void shouldCallRecordReservationFillWithParsedFields() {
        consumer.consume(record(validPayloadJson()));

        verify(orderService).recordReservationFill(
                eq(userId), eq("005930"), eq(OrderSideValue.SELL), eq(10L), eq(70_000L), eq(0L), eq(reservationId));
    }

    @Test
    @DisplayName("역직렬화 실패 시 예외 없이 skip한다")
    void shouldSkipOnDeserializationFailure() {
        assertThatCode(() -> consumer.consume(record("{broken")))
                .doesNotThrowAnyException();

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("side 값이 enum에 없으면 poison pill로 간주해 skip한다")
    void shouldSkipOnInvalidSideEnumValue() {
        String invalid = """
                {"reservationId":"%s","userId":"%s","accountId":"%s","symbol":"005930",
                 "side":"HOLD","quantity":10,"executedPrice":70000,"reservedAmount":0}
                """.formatted(reservationId, userId, UUID.randomUUID());

        assertThatCode(() -> consumer.consume(record(invalid))).doesNotThrowAnyException();

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("idempotencyKey unique 위반(DataIntegrityViolationException)은 이미 처리된 건으로 skip한다")
    void shouldSkipOnDataIntegrityViolationException() {
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(orderService).recordReservationFill(any(), any(), any(), anyLong(), anyLong(), anyLong(), any());

        assertThatCode(() -> consumer.consume(record(validPayloadJson())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("예상치 못한 오류는 RuntimeException으로 감싸 재throw해 재시도를 유도한다")
    void shouldRethrowOnUnexpectedException() {
        doThrow(new RuntimeException("DB timeout"))
                .when(orderService).recordReservationFill(any(), any(), any(), anyLong(), anyLong(), anyLong(), any());

        assertThatThrownBy(() -> consumer.consume(record(validPayloadJson())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ReservationExecuted 처리 실패");
    }
}

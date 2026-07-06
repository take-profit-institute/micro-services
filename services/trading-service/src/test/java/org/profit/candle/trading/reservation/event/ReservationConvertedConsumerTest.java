package org.profit.candle.trading.reservation.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.service.ReservationBatchService;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationConvertedConsumerTest {

    @Mock private ReservationBatchService reservationBatchService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ReservationConvertedConsumer consumer;

    private final UUID reservationId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new ReservationConvertedConsumer(reservationBatchService, objectMapper);
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("trading.order.ReservationConverted", 0, 0L,
                reservationId.toString(), json);
    }

    private String validPayloadJson() {
        return """
                {"reservationId":"%s","orderId":"%s","userId":"%s"}
                """.formatted(reservationId, orderId, UUID.randomUUID());
    }

    @Test
    @DisplayName("정상 페이로드 수신 시 markConverted를 호출한다")
    void shouldCallMarkConvertedWithParsedIds() {
        consumer.consume(record(validPayloadJson()));

        verify(reservationBatchService).markConverted(reservationId, orderId);
    }

    @Test
    @DisplayName("역직렬화 실패 시 예외 없이 skip한다")
    void shouldSkipOnDeserializationFailure() {
        assertThatCode(() -> consumer.consume(record("{{invalid")))
                .doesNotThrowAnyException();

        verifyNoInteractions(reservationBatchService);
    }

    @Test
    @DisplayName("이미 EXECUTED/CANCELLED 등 상태 전이가 불가능하면 재시도 없이 skip한다")
    void shouldSkipWhenReservationExceptionThrown() {
        doThrow(new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND))
                .when(reservationBatchService).markConverted(any(), any());

        assertThatCode(() -> consumer.consume(record(validPayloadJson())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UUID 파싱 실패(poison pill)도 재시도 없이 skip한다")
    void shouldSkipOnMalformedUuidInPayload() {
        String malformed = """
                {"reservationId":"not-a-uuid","orderId":"%s","userId":"%s"}
                """.formatted(orderId, UUID.randomUUID());

        assertThatCode(() -> consumer.consume(record(malformed)))
                .doesNotThrowAnyException();

        verifyNoInteractions(reservationBatchService);
    }

    @Test
    @DisplayName("일시적 오류는 RuntimeException으로 감싸 재throw해 재시도를 유도한다")
    void shouldRethrowOnUnexpectedException() {
        doThrow(new RuntimeException("DB 장애"))
                .when(reservationBatchService).markConverted(any(), any());

        assertThatThrownBy(() -> consumer.consume(record(validPayloadJson())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ReservationConverted 처리 실패");
    }
}

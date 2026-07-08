package org.profit.candle.trading.reservation.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.reservation.service.ReservationBatchService;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationMarketPriceConsumerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock private ReservationBatchService reservationBatchService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    // KST 기준 2026-07-06 09:05 고정 — UTC로는 2026-07-06 00:05
    private final Clock fixedClock = Clock.fixed(
            LocalDateTime.of(2026, 7, 6, 9, 5).atZone(KST).toInstant(), KST);
    private ReservationMarketPriceConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReservationMarketPriceConsumer(reservationBatchService, objectMapper, fixedClock);
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("market.price.v1", 0, 0L, "005930", json);
    }

    private ConsumerRecord<String, Object> objectRecord(Object payload) {
        return new ConsumerRecord<>("market.price.v1", 0, 0L, "005930", payload);
    }

    @Test
    @DisplayName("현재가 이벤트 수신 시 KST 기준 오늘 날짜로 processOpenMarketReservations를 호출한다")
    void shouldProcessOpenMarketReservationsWithTodayKstDate() {
        consumer.consume(record("""
                {"symbol":"005930","price":70000}
                """));

        verify(reservationBatchService).processOpenMarketReservations(
                LocalDate.of(2026, 7, 6), "005930", 70_000L);
    }

    @Test
    @DisplayName("추가 필드가 붙어도 무시하고 처리한다")
    void shouldIgnoreUnknownFields() {
        consumer.consume(record("""
                {"symbol":"005930","price":70000,"source":"websocket","quotedAt":"2026-07-08T10:00:00Z"}
                """));

        verify(reservationBatchService).processOpenMarketReservations(
                LocalDate.of(2026, 7, 6), "005930", 70_000L);
    }

    @Test
    @DisplayName("LinkedHashMap payload도 MarketPriceEvent로 변환해 처리한다")
    void shouldHandleLinkedHashMapPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("symbol", "005930");
        payload.put("price", 70_000);
        payload.put("ignored", "field");

        consumer.consume(objectRecord(payload));

        verify(reservationBatchService).processOpenMarketReservations(
                LocalDate.of(2026, 7, 6), "005930", 70_000L);
    }

    @Test
    @DisplayName("역직렬화 실패 시 예외 없이 skip한다")
    void shouldSkipOnDeserializationFailure() {
        assertThatCode(() -> consumer.consume(record("not-json")))
                .doesNotThrowAnyException();

        verifyNoInteractions(reservationBatchService);
    }

    @Test
    @DisplayName("처리 실패(보상 실패 포함) 시 RuntimeException으로 감싸 재throw해 재시도를 유도한다")
    void shouldRethrowWhenProcessingFails() {
        doThrow(new RuntimeException("잔고 보상 실패"))
                .when(reservationBatchService)
                .processOpenMarketReservations(any(), anyString(), anyLong());

        assertThatThrownBy(() -> consumer.consume(record("""
                {"symbol":"005930","price":70000}
                """)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OPEN+MARKET 체결 처리 실패");
    }
}

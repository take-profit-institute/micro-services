package org.profit.candle.portfolio.holding.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.portfolio.holding.event.dto.OrderFilledPayload;
import org.profit.candle.portfolio.holding.event.entity.ConsumedEvent;
import org.profit.candle.portfolio.holding.event.repository.ConsumedEventRepository;
import org.profit.candle.portfolio.holding.service.HoldingService;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingEventConsumerTest {

    @Mock HoldingService holdingService;
    @Mock ConsumedEventRepository consumedEventRepository;

    TradingEventConsumer consumer;
    ObjectMapper objectMapper;

    private static final String USER_ID = "user-1";
    private static final String SYMBOL = "005930";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        consumer = new TradingEventConsumer(holdingService, consumedEventRepository, objectMapper);
    }

    private String payload(UUID eventId, String side, long qty, long price) throws Exception {
        return objectMapper.writeValueAsString(
                new OrderFilledPayload(eventId, "OrderFilled", 1,
                        USER_ID, SYMBOL, side, qty, price, Instant.now()));
    }

    // ─── BUY / SELL 정상 처리 ────────────────────────────────────────────────

    @Test
    void onOrderFilled_buyEvent_callsApplyBuyFillAndSavesConsumedEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onOrderFilled(payload(eventId, "BUY", 10, 75_000));

        verify(holdingService).applyBuyFill(USER_ID, SYMBOL, 10L, 75_000L);
        ArgumentCaptor<ConsumedEvent> captor = ArgumentCaptor.forClass(ConsumedEvent.class);
        verify(consumedEventRepository).save(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(eventId);
        assertThat(captor.getValue().eventType()).isEqualTo("OrderFilled");
    }

    @Test
    void onOrderFilled_sellEvent_callsApplySellFillAndSavesConsumedEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onOrderFilled(payload(eventId, "SELL", 5, 80_000));

        verify(holdingService).applySellFill(USER_ID, SYMBOL, 5L, 80_000L);
        verify(consumedEventRepository).save(any(ConsumedEvent.class));
    }

    @Test
    void onOrderFilled_sideIsCaseInsensitive_buy() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onOrderFilled(payload(eventId, "buy", 10, 75_000));

        verify(holdingService).applyBuyFill(USER_ID, SYMBOL, 10L, 75_000L);
    }

    // ─── 중복 / 오류 처리 ────────────────────────────────────────────────────

    @Test
    void onOrderFilled_duplicateEvent_skipsProcessingAndSave() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(consumedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.onOrderFilled(payload(eventId, "BUY", 10, 75_000));

        verify(holdingService, never()).applyBuyFill(any(), any(), any(Long.class), any(Long.class));
        verify(consumedEventRepository, never()).save(any());
    }

    @Test
    void onOrderFilled_invalidJson_logsAndReturnsWithoutSaving() {
        consumer.onOrderFilled("{invalid-json}");

        verify(holdingService, never()).applyBuyFill(any(), any(), any(Long.class), any(Long.class));
        verify(consumedEventRepository, never()).save(any());
    }

    @Test
    void onOrderFilled_unknownSide_logsAndReturnsWithoutSaving() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onOrderFilled(payload(eventId, "UNKNOWN", 10, 75_000));

        verify(holdingService, never()).applyBuyFill(any(), any(), any(Long.class), any(Long.class));
        verify(holdingService, never()).applySellFill(any(), any(), any(Long.class), any(Long.class));
        verify(consumedEventRepository, never()).save(any());
    }

    @Test
    void onOrderFilled_serviceThrows_rethrowsForKafkaRetry() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
                .when(holdingService).applyBuyFill(any(), any(), any(Long.class), any(Long.class));

        assertThatThrownBy(() -> consumer.onOrderFilled(payload(eventId, "BUY", 10, 75_000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");

        verify(consumedEventRepository, never()).save(any());
    }
}

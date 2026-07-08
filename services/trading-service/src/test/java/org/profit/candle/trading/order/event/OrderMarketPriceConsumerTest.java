package org.profit.candle.trading.order.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.order.service.CachedMarketPriceProvider;
import org.profit.candle.trading.order.service.OrderExecutionService;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderMarketPriceConsumerTest {

    @Mock private CachedMarketPriceProvider cachedMarketPriceProvider;
    @Mock private OrderExecutionService orderExecutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderMarketPriceConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderMarketPriceConsumer(cachedMarketPriceProvider, orderExecutionService, objectMapper);
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("market.open-price.v1", 0, 0L, "005930", json);
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 캐시 갱신 후 지정가 조건 체결을 스캔한다")
    void shouldUpdateCacheAndFillLimitOrders() {
        when(orderExecutionService.fillLimitOrdersIfConditionMet("005930", 70_000L)).thenReturn(2);

        consumer.consume(record("""
                {"symbol":"005930","price":70000}
                """));

        verify(cachedMarketPriceProvider).updatePrice("005930", 70_000L);
        verify(orderExecutionService).fillLimitOrdersIfConditionMet("005930", 70_000L);
    }

    @Test
    @DisplayName("역직렬화 실패 시 캐시/체결 스캔 없이 조용히 skip한다")
    void shouldSkipOnDeserializationFailure() {
        assertThatCode(() -> consumer.consume(record("not-json")))
                .doesNotThrowAnyException();

        verifyNoInteractions(cachedMarketPriceProvider, orderExecutionService);
    }

    @Test
    @DisplayName("symbol이 빈 문자열이면 캐시 갱신 없이 skip한다")
    void shouldSkipWhenSymbolIsBlank() {
        consumer.consume(record("""
                {"symbol":"","price":70000}
                """));

        verifyNoInteractions(cachedMarketPriceProvider, orderExecutionService);
    }

    @Test
    @DisplayName("price가 0 이하이면 캐시 갱신 없이 skip한다")
    void shouldSkipWhenPriceIsZeroOrNegative() {
        consumer.consume(record("""
                {"symbol":"005930","price":0}
                """));

        verifyNoInteractions(cachedMarketPriceProvider, orderExecutionService);
    }

    @Test
    @DisplayName("체결 스캔 중 예외가 나면 RuntimeException으로 감싸 재throw해 재시도를 유도한다")
    void shouldRethrowWhenFillLimitOrdersThrows() {
        when(orderExecutionService.fillLimitOrdersIfConditionMet(any(), anyLong()))
                .thenThrow(new RuntimeException("일시적 락 경합"));

        assertThatThrownBy(() -> consumer.consume(record("""
                {"symbol":"005930","price":70000}
                """)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("현재가 이벤트 처리 실패");

        // 캐시 갱신은 체결 스캔보다 먼저 실행되므로, 실패해도 캐시는 이미 최신화된 상태로 남는다.
        verify(cachedMarketPriceProvider).updatePrice("005930", 70_000L);
    }
}

package org.profit.candle.ranking.ranking.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.ranking.ranking.service.RankingParticipantProjectionService;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OrderFilledConsumerTest {

    @Mock
    ObjectMapper objectMapper;

    @Mock
    RankingParticipantProjectionService projectionService;

    @InjectMocks
    OrderFilledConsumer consumer;

    /** 정상 OrderFilled가 거래 횟수 투영으로 전달되는지 검증한다. */
    @Test
    void onOrderFilledProjectsAValidEvent() throws Exception {
        OrderFilledEvent event = event(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1L);
        when(objectMapper.readValue("{}", OrderFilledEvent.class)).thenReturn(event);

        consumer.onOrderFilled("{}");

        verify(projectionService).projectFilledOrder(event);
    }

    /** orderId 형식이나 체결 수량이 잘못된 이벤트를 반영하지 않는지 검증한다. */
    @Test
    void onOrderFilledIgnoresAnInvalidEvent() throws Exception {
        OrderFilledEvent event = event("invalid-order", UUID.randomUUID().toString(), 0L);
        when(objectMapper.readValue("{}", OrderFilledEvent.class)).thenReturn(event);

        consumer.onOrderFilled("{}");

        verify(projectionService, never()).projectFilledOrder(org.mockito.ArgumentMatchers.any());
    }

    /** DB 투영 실패를 숨기지 않아 Kafka 재시도가 가능하도록 하는지 검증한다. */
    @Test
    void onOrderFilledDoesNotHideProjectionFailures() throws Exception {
        OrderFilledEvent event = event(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1L);
        when(objectMapper.readValue("{}", OrderFilledEvent.class)).thenReturn(event);
        doThrow(new IllegalStateException("database unavailable"))
                .when(projectionService).projectFilledOrder(event);

        assertThatThrownBy(() -> consumer.onOrderFilled("{}"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Trading의 현재 OrderFilled payload와 같은 테스트 이벤트를 만든다. */
    private OrderFilledEvent event(String orderId, String userId, long quantity) {
        return new OrderFilledEvent(
                orderId, userId, "005930", "BUY",
                80_000L, quantity, 10L, 0L, 80_010L);
    }
}

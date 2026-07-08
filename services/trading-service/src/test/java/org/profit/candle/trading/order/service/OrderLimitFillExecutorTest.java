package org.profit.candle.trading.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.order.entity.ExecutionEntity;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.event.OrderOutboxOperations;
import org.profit.candle.trading.order.repository.ExecutionRepository;
import org.profit.candle.trading.order.repository.OrderRepository;
import org.profit.candle.trading.support.event.OutboxWriter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * EXE-002 지정가 조건 체결의 건별 트랜잭션 단위 (Self-invocation 방지용으로 분리된 클래스).
 * "락 획득 후 조건 재검증" 분기를 핵심적으로 검증한다 — 캐시 조회와 락 획득 사이 가격 변동 케이스.
 */
@ExtendWith(MockitoExtension.class)
class OrderLimitFillExecutorTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ExecutionRepository executionRepository;
    @Mock private AccountService accountService;
    @Mock private OutboxWriter outboxWriter;
    @Mock private OrderOutboxOperations outboxOperations;

    private OrderLimitFillExecutor executor;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-07-06T02:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        executor = new OrderLimitFillExecutor(orderRepository, executionRepository, accountService,
                outboxWriter, outboxOperations, fixedClock);
    }

    @Nested
    @DisplayName("fillIfConditionMet")
    class FillIfConditionMet {

        @Test
        void shouldFillBuyOrderWhenCurrentPriceIsBelowOrEqualToLimitPrice() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "idem-limit-1");
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

            boolean filled = executor.fillIfConditionMet(order.getId(), 65_000L);

            assertThat(filled).isTrue();
            assertThat(order.getStatus().name()).isEqualTo("FILLED");
            verify(executionRepository).save(any(ExecutionEntity.class));
        }

        @Test
        void shouldNotFillBuyOrderWhenCurrentPriceIsAboveLimitPriceEvenIfPreFiltered() {
            // 후보 조회 시점엔 조건을 만족했지만, 락 획득 사이 가격이 올라간 상황을 재현.
            // executionRepository.findByOrderId는 가격 조건 검사보다 먼저 실행되므로
            // (fillIfConditionMet 내부 순서: order 조회 → 중복체결 조회 → 조건 검사),
            // 이 mock과의 상호작용 자체는 발생한다 — save()가 안 불렸는지만 검증한다.
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "idem-limit-2");
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

            boolean filled = executor.fillIfConditionMet(order.getId(), 75_000L);

            assertThat(filled).isFalse();
            verify(executionRepository, never()).save(any());
            verifyNoInteractions(accountService);
        }

        @Test
        void shouldReturnFalseWhenOrderNoLongerExists() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

            boolean filled = executor.fillIfConditionMet(orderId, 65_000L);

            assertThat(filled).isFalse();
        }

        @Test
        void shouldReturnFalseWhenOrderAlreadyFilledByAnotherTransaction() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "idem-limit-3");
            order.fill();
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

            boolean filled = executor.fillIfConditionMet(order.getId(), 65_000L);

            assertThat(filled).isFalse();
        }

        @Test
        void shouldFillSellOrderWhenCurrentPriceIsAboveOrEqualToLimitPrice() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.SELL,
                    OrderKindValue.LIMIT, 10, 70_000L, 0L, "idem-limit-4");
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

            boolean filled = executor.fillIfConditionMet(order.getId(), 75_000L);

            assertThat(filled).isTrue();
            verify(accountService).settleSell(eq(userId), anyLong());
        }
    }
}
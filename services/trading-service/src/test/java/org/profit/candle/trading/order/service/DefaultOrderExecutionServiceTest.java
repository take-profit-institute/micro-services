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
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.profit.candle.trading.order.repository.ExecutionRepository;
import org.profit.candle.trading.order.repository.OrderRepository;
import org.profit.candle.trading.support.event.OutboxWriter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 컨벤션 12장: EXE-001/002 체결 계산(수수료/세금/net_amount)은 서비스 흐름 테스트에서
 * BigDecimal 반올림/overflow 케이스를 반드시 커버한다.
 */
@ExtendWith(MockitoExtension.class)
class DefaultOrderExecutionServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ExecutionRepository executionRepository;
    @Mock private AccountService accountService;
    @Mock private MarketPriceProvider marketPriceProvider;
    @Mock private OutboxWriter outboxWriter;
    @Mock private OrderOutboxOperations outboxOperations;
    @Mock private OrderLimitFillExecutor limitFillExecutor;

    private DefaultOrderExecutionService executionService;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-07-06T02:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        executionService = new DefaultOrderExecutionService(orderRepository, executionRepository,
                accountService, marketPriceProvider, outboxWriter, outboxOperations,
                limitFillExecutor, fixedClock);
    }

    @Nested
    @DisplayName("fillMarketOrder")
    class FillMarketOrder {

        @Test
        void shouldSettleBuyWithGrossPlusFee() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.MARKET, 10, null, 0L, "idem-fill-1");
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());
            when(marketPriceProvider.getCurrentPriceKrw("005930")).thenReturn(70_000L);

            executionService.fillMarketOrder(order.getId());

            // gross=700,000 fee=105(0.015%) net=700,105
            verify(accountService).settleBuy(userId, 0L, 700_105L);
            verify(executionRepository).save(any(ExecutionEntity.class));
            assertThat(order.getStatus().name()).isEqualTo("FILLED");
        }

        @Test
        void shouldSettleSellWithGrossMinusFeeMinusTax() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.SELL,
                    OrderKindValue.MARKET, 10, null, 0L, "idem-fill-2");
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());
            when(marketPriceProvider.getCurrentPriceKrw("005930")).thenReturn(70_000L);

            executionService.fillMarketOrder(order.getId());

            // gross=700,000 fee=105(0.015%) tax=1,260(0.18%) net=698,635
            verify(accountService).settleSell(userId, 698_635L);
        }

        @Test
        void shouldRejectWhenOrderIsNotPending() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.MARKET, 10, null, 0L, "idem-fill-3");
            order.fill();
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> executionService.fillMarketOrder(order.getId()))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_PENDING);
        }

        @Test
        void shouldRejectDoubleFillWhenExecutionAlreadyExists() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.MARKET, 10, null, 0L, "idem-fill-4");
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(executionRepository.findByOrderId(order.getId()))
                    .thenReturn(Optional.of(mock(ExecutionEntity.class)));

            assertThatThrownBy(() -> executionService.fillMarketOrder(order.getId()))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_PENDING);
        }
    }

    @Nested
    @DisplayName("fillReservationOrder")
    class FillReservationOrder {

        @Test
        void shouldSettleBuyUsingReservedAmountAsNetToAvoidRoundingMismatch() {
            // 예약 배치가 이미 선점(lock)한 reserved_amount(=gross+fee)를 그대로 정산한다.
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.MARKET, 10, null, 700_105L, "idem-resv-1");
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

            executionService.fillReservationOrder(order.getId(), 70_000L);

            verify(accountService).settleBuy(userId, 700_105L, 700_105L);
        }

        @Test
        void shouldComputeFeeAndTaxForSellReservationFill() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.SELL,
                    OrderKindValue.MARKET, 10, null, 0L, "idem-resv-2");
            when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

            executionService.fillReservationOrder(order.getId(), 70_000L);

            verify(accountService).settleSell(userId, 698_635L);
        }
    }

    @Nested
    @DisplayName("fillLimitOrdersIfConditionMet")
    class FillLimitOrdersIfConditionMet {

        @Test
        void shouldOnlyDelegateCandidatesThatMeetPriceCondition() {
            OrderRepository.LimitOrderCandidate buyMeets = candidate(OrderSideValue.BUY, 70_000L);
            OrderRepository.LimitOrderCandidate buyNotMeets = candidate(OrderSideValue.BUY, 60_000L);
            when(orderRepository.findPendingLimitOrdersBySymbol(eq("005930"), any()))
                    .thenReturn(List.of(buyMeets, buyNotMeets));
            when(limitFillExecutor.fillIfConditionMet(eq(buyMeets.getId()), eq(65_000L)))
                    .thenReturn(true);

            int count = executionService.fillLimitOrdersIfConditionMet("005930", 65_000L);

            assertThat(count).isEqualTo(1);
            verify(limitFillExecutor).fillIfConditionMet(buyMeets.getId(), 65_000L);
            verify(limitFillExecutor, never()).fillIfConditionMet(eq(buyNotMeets.getId()), anyLong());
        }

        @Test
        void shouldPropagateFailureAfterProcessingRemainingCandidates() {
            OrderRepository.LimitOrderCandidate first = candidate(OrderSideValue.BUY, 70_000L);
            OrderRepository.LimitOrderCandidate second = candidate(OrderSideValue.BUY, 80_000L);
            when(orderRepository.findPendingLimitOrdersBySymbol(eq("005930"), any()))
                    .thenReturn(List.of(first, second));
            when(limitFillExecutor.fillIfConditionMet(eq(first.getId()), anyLong()))
                    .thenThrow(new RuntimeException("일시적 락 경합"));
            when(limitFillExecutor.fillIfConditionMet(eq(second.getId()), anyLong()))
                    .thenReturn(true);

            assertThatThrownBy(() -> executionService.fillLimitOrdersIfConditionMet("005930", 65_000L))
                    .isInstanceOf(RuntimeException.class);

            // 실패한 건이 있어도 나머지 후보는 계속 처리한다.
            verify(limitFillExecutor).fillIfConditionMet(second.getId(), 65_000L);
        }

        private OrderRepository.LimitOrderCandidate candidate(OrderSideValue side, long priceKrw) {
            OrderRepository.LimitOrderCandidate candidate = mock(OrderRepository.LimitOrderCandidate.class);
            when(candidate.getId()).thenReturn(UUID.randomUUID());
            when(candidate.getSide()).thenReturn(side);
            when(candidate.getPriceKrw()).thenReturn(priceKrw);
            return candidate;
        }
    }

    /**
     * EXE-002 보완: placeOrder/amendOrder 직후 이 주문 1건에 대해서만 현재가와 즉시 비교한다.
     * 시세 tick을 기다리지 않고, "부른 값이 이미 시장가를 이긴 경우"를 접수 시점에 바로 잡는다.
     */
    @Nested
    @DisplayName("fillLimitOrderIfConditionMetOnPlacement")
    class FillLimitOrderIfConditionMetOnPlacement {

        @Test
        void shouldFillImmediatelyWhenBuyPriceIsAboveOrEqualCurrentPrice() {
            // 매수 지정가 72,000원 > 현재가 71,000원 → "더 비싸게라도 사겠다"는 뜻이라 즉시 체결
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.LIMIT, 1, 72_000L, 72_010L, "idem-imm-1");
            when(marketPriceProvider.getCurrentPriceKrw("005930")).thenReturn(71_000L);

            executionService.fillLimitOrderIfConditionMetOnPlacement(order);

            verify(limitFillExecutor).fillIfConditionMet(order.getId(), 71_000L);
        }

        @Test
        void shouldNotFillWhenBuyPriceIsBelowCurrentPrice() {
            // 매수 지정가 70,000원 < 현재가 71,000원 → 아직 조건 미충족, PENDING 유지
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.LIMIT, 1, 70_000L, 70_010L, "idem-imm-2");
            when(marketPriceProvider.getCurrentPriceKrw("005930")).thenReturn(71_000L);

            executionService.fillLimitOrderIfConditionMetOnPlacement(order);

            verifyNoInteractions(limitFillExecutor);
        }

        @Test
        void shouldFillImmediatelyWhenSellPriceIsBelowOrEqualCurrentPrice() {
            // 매도 지정가 70,000원 < 현재가 71,000원 → "더 싸게라도 팔겠다"는 뜻이라 즉시 체결
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.SELL,
                    OrderKindValue.LIMIT, 1, 70_000L, 0L, "idem-imm-3");
            when(marketPriceProvider.getCurrentPriceKrw("005930")).thenReturn(71_000L);

            executionService.fillLimitOrderIfConditionMetOnPlacement(order);

            verify(limitFillExecutor).fillIfConditionMet(order.getId(), 71_000L);
        }

        @Test
        void shouldNotFillWhenSellPriceIsAboveCurrentPrice() {
            // 매도 지정가 72,000원 > 현재가 71,000원 → 아직 조건 미충족, PENDING 유지
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.SELL,
                    OrderKindValue.LIMIT, 1, 72_000L, 0L, "idem-imm-4");
            when(marketPriceProvider.getCurrentPriceKrw("005930")).thenReturn(71_000L);

            executionService.fillLimitOrderIfConditionMetOnPlacement(order);

            verifyNoInteractions(limitFillExecutor);
        }

        @Test
        void shouldStayPendingWithoutThrowingWhenPriceLookupFails() {
            // market-service 장애 등으로 현재가 조회 자체가 실패해도 접수 자체를 실패시키지 않는다.
            // 이후 tick 기반 EXE-002(fillLimitOrdersIfConditionMet)가 정상적으로 잡아준다.
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.LIMIT, 1, 72_000L, 72_010L, "idem-imm-5");
            when(marketPriceProvider.getCurrentPriceKrw("005930"))
                    .thenThrow(new OrderException(OrderErrorCode.MARKET_PRICE_UNAVAILABLE));

            executionService.fillLimitOrderIfConditionMetOnPlacement(order);

            verifyNoInteractions(limitFillExecutor);
        }
    }
}
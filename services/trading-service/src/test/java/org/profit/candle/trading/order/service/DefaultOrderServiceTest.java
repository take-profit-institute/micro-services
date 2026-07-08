package org.profit.candle.trading.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.order.dto.AmendOrderCommand;
import org.profit.candle.trading.order.dto.CancelResult;
import org.profit.candle.trading.order.dto.PlaceOrderCommand;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderSideValue;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.profit.candle.trading.order.event.OrderOutboxOperations;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;
import org.profit.candle.trading.order.repository.OrderRepository;
import org.profit.candle.trading.support.event.OutboxWriter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DefaultOrderService 흐름 테스트 — repository/client는 mock, outbox 기록 자체는
 * "호출됐는지"만 검증한다 (실제 outbox 커밋/발행은 통합 테스트 영역).
 */
@ExtendWith(MockitoExtension.class)
class DefaultOrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private AccountService accountService;
    @Mock private OutboxWriter outboxWriter;
    @Mock private OrderOutboxOperations outboxOperations;
    @Mock private TradingHoursValidator tradingHoursValidator;
    @Mock private OrderExecutionService orderExecutionService;

    private DefaultOrderService orderService;

    private UUID userId;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        orderService = new DefaultOrderService(orderRepository, accountService, outboxWriter,
                outboxOperations, tradingHoursValidator, orderExecutionService);
        userId = UUID.randomUUID();
        account = AccountEntity.create(userId);
    }

    @Nested
    @DisplayName("placeOrder")
    class PlaceOrder {

        @Test
        void shouldRejectWhenMarketIsClosed() {
            doThrow(new OrderException(OrderErrorCode.OUTSIDE_TRADING_HOURS))
                    .when(tradingHoursValidator).requireMarketOpen();

            PlaceOrderCommand command = new PlaceOrderCommand(
                    "005930", OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, "idem-1");

            assertThatThrownBy(() -> orderService.placeOrder(userId, command))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.OUTSIDE_TRADING_HOURS);

            verifyNoInteractions(accountService, orderRepository);
        }

        @Test
        void shouldRejectDuplicatePendingOrderForSameSymbolAndSameSide() {
            when(accountService.getAccount(userId)).thenReturn(account);
            when(orderRepository.existsByAccountIdAndSymbolAndSideAndStatus(
                    account.getId(), "005930", OrderSideValue.BUY, OrderStatusValue.PENDING))
                    .thenReturn(true);

            PlaceOrderCommand command = new PlaceOrderCommand(
                    "005930", OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, "idem-2");

            assertThatThrownBy(() -> orderService.placeOrder(userId, command))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.DUPLICATE_PENDING_ORDER);
        }

        @Test
        void shouldAllowSellOrderWhenPendingBuyExistsForSameSymbol() {
            // 매수 PENDING이 있어도 매도는 별개 side라 막히면 안 된다 — UX 회귀 방지 테스트.
            // placeOrder는 command.side()(SELL)로만 조회하므로 BUY 쪽 stub은 실제로 호출되지 않는다
            // (Mockito strict stubbing에 unnecessary로 잡혀 제거함) — SELL false만 스텁하면 충분하다.
            when(accountService.getAccount(userId)).thenReturn(account);
            when(orderRepository.existsByAccountIdAndSymbolAndSideAndStatus(
                    account.getId(), "005930", OrderSideValue.SELL, OrderStatusValue.PENDING))
                    .thenReturn(false);

            PlaceOrderCommand command = new PlaceOrderCommand(
                    "005930", OrderSideValue.SELL, OrderKindValue.LIMIT, 10, 70_000L, "idem-2-sell");

            OrderEntity result = orderService.placeOrder(userId, command);

            assertThat(result).isNotNull();
            verify(orderRepository).save(any(OrderEntity.class));
        }

        @Test
        void shouldLockBalanceIncludingFeeForBuyLimitOrder() {
            when(accountService.getAccount(userId)).thenReturn(account);
            when(orderRepository.existsByAccountIdAndSymbolAndSideAndStatus(any(), anyString(), any(), any()))
                    .thenReturn(false);

            PlaceOrderCommand command = new PlaceOrderCommand(
                    "005930", OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, "idem-3");

            orderService.placeOrder(userId, command);

            // 700,000원 * 0.00015 = 105원 수수료 → 700,105원 잠금
            verify(accountService).lockBalance(userId, 700_105L);
            verify(orderRepository).save(any(OrderEntity.class));
            verify(outboxWriter).record(eq(outboxOperations), eq("OrderPlaced"), anyString(), any());
        }

        @Test
        void shouldNotLockBalanceForSellLimitOrder() {
            when(accountService.getAccount(userId)).thenReturn(account);
            when(orderRepository.existsByAccountIdAndSymbolAndSideAndStatus(any(), anyString(), any(), any()))
                    .thenReturn(false);

            PlaceOrderCommand command = new PlaceOrderCommand(
                    "005930", OrderSideValue.SELL, OrderKindValue.LIMIT, 10, 70_000L, "idem-4");

            orderService.placeOrder(userId, command);

            verify(accountService, never()).lockBalance(any(), anyLong());
        }

        @Test
        void shouldTriggerImmediateFillForMarketOrder() {
            when(accountService.getAccount(userId)).thenReturn(account);
            when(orderRepository.existsByAccountIdAndSymbolAndSideAndStatus(any(), anyString(), any(), any()))
                    .thenReturn(false);
            OrderEntity filled = OrderEntity.place(userId, account.getId(), "005930",
                    OrderSideValue.BUY, OrderKindValue.MARKET, 10, null, 0L, "idem-5");
            when(orderExecutionService.fillMarketOrder(any())).thenReturn(filled);

            PlaceOrderCommand command = new PlaceOrderCommand(
                    "005930", OrderSideValue.BUY, OrderKindValue.MARKET, 10, 0L, "idem-5");

            OrderEntity result = orderService.placeOrder(userId, command);

            verify(orderExecutionService).fillMarketOrder(any());
            assertThat(result).isEqualTo(filled);
        }

        @Test
        void shouldRejectZeroQuantity() {
            PlaceOrderCommand command = new PlaceOrderCommand(
                    "005930", OrderSideValue.BUY, OrderKindValue.LIMIT, 0, 70_000L, "idem-6");

            assertThatThrownBy(() -> orderService.placeOrder(userId, command))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("placeOrderFromReservation")
    class PlaceOrderFromReservation {

        @Test
        void shouldReplayExistingOrderAndReemitReservationConvertedOnIdempotentRetry() {
            OrderEntity existing = OrderEntity.place(userId, account.getId(), "005930",
                    OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "reservation-idem");
            when(accountService.getAccount(userId)).thenReturn(account);
            when(orderRepository.findByIdempotencyKey("reservation-idem"))
                    .thenReturn(Optional.of(existing));

            PlaceOrderCommand command = new PlaceOrderCommand(
                    "005930", OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, "reservation-idem");

            OrderEntity result = orderService.placeOrderFromReservation(
                    userId, command, 700_105L, UUID.randomUUID());

            assertThat(result).isEqualTo(existing);
            verify(orderRepository, never()).save(any());
            // 재수신 시에도 ReservationConverted를 다시 발행해 CONVERTING에 stuck되지 않게 한다.
            verify(outboxWriter).record(eq(outboxOperations), eq("ReservationConverted"), anyString(), any());
        }

        @Test
        void shouldCreateOrderAndEmitBothOutboxEventsInSameTransactionWhenNotExists() {
            when(accountService.getAccount(userId)).thenReturn(account);
            when(orderRepository.findByIdempotencyKey("reservation-idem-2"))
                    .thenReturn(Optional.empty());

            PlaceOrderCommand command = new PlaceOrderCommand(
                    "005930", OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, "reservation-idem-2");

            orderService.placeOrderFromReservation(userId, command, 700_105L, UUID.randomUUID());

            verify(orderRepository).save(any(OrderEntity.class));
            verify(outboxWriter).record(eq(outboxOperations), eq("ReservationConverted"), anyString(), any());
            verify(outboxWriter).record(eq(outboxOperations), eq("OrderPlaced"), anyString(), any());
            // 예약 전환 경로는 거래시간 검증/lockBalance 대상이 아니다.
            verifyNoInteractions(tradingHoursValidator);
            verify(accountService, never()).lockBalance(any(), anyLong());
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        void shouldReleaseReservedBalanceOnCancelForBuyOrder() {
            OrderEntity order = OrderEntity.place(userId, account.getId(), "005930",
                    OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "idem-cancel");
            when(orderRepository.findByIdAndUserIdForUpdate(order.getId(), userId))
                    .thenReturn(Optional.of(order));

            CancelResult result = orderService.cancelOrder(userId, order.getId());

            assertThat(result.order().getStatus()).isEqualTo(OrderStatusValue.CANCELLED);
            assertThat(result.releasedAmount()).isEqualTo(700_105L);
            verify(accountService).releaseBalance(userId, 700_105L);
            verify(outboxWriter).record(eq(outboxOperations), eq("OrderCancelled"), anyString(), any());
        }

        @Test
        void shouldNotReleaseBalanceForSellOrder() {
            OrderEntity order = OrderEntity.place(userId, account.getId(), "005930",
                    OrderSideValue.SELL, OrderKindValue.LIMIT, 10, 70_000L, 0L, "idem-cancel-sell");
            when(orderRepository.findByIdAndUserIdForUpdate(order.getId(), userId))
                    .thenReturn(Optional.of(order));

            orderService.cancelOrder(userId, order.getId());

            verify(accountService, never()).releaseBalance(any(), anyLong());
        }

        @Test
        void shouldThrowOrderNotFoundWhenOrderDoesNotBelongToUser() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findByIdAndUserIdForUpdate(orderId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void shouldRejectCancelOfAlreadyFilledOrder() {
            OrderEntity order = OrderEntity.place(userId, account.getId(), "005930",
                    OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "idem-filled");
            order.fill();
            when(orderRepository.findByIdAndUserIdForUpdate(order.getId(), userId))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(userId, order.getId()))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_PENDING);
        }
    }

    @Nested
    @DisplayName("amendOrder")
    class Amend {

        @Test
        void shouldCancelOriginalAndCreateAmendedOrderLinkedByParentId() {
            OrderEntity original = OrderEntity.place(userId, account.getId(), "005930",
                    OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "idem-orig");
            when(orderRepository.findByIdAndUserIdForUpdate(original.getId(), userId))
                    .thenReturn(Optional.of(original));

            AmendOrderCommand command = new AmendOrderCommand(5, 80_000L, "idem-amend");

            OrderEntity amended = orderService.amendOrder(userId, original.getId(), command);

            assertThat(original.getStatus()).isEqualTo(OrderStatusValue.CANCELLED);
            assertThat(amended.getParentOrderId()).isEqualTo(original.getId());
            assertThat(amended.getQuantity()).isEqualTo(5);
            assertThat(amended.getPriceKrw()).isEqualTo(80_000L);
            // 원 주문 잠금 해제 + 신규 주문 잠금 각 1회
            verify(accountService).releaseBalance(userId, 700_105L);
            verify(accountService).lockBalance(eq(userId), anyLong());
        }

        @Test
        void shouldRejectAmendWithInvalidQuantity() {
            OrderEntity original = OrderEntity.place(userId, account.getId(), "005930",
                    OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "idem-orig-2");
            when(orderRepository.findByIdAndUserIdForUpdate(original.getId(), userId))
                    .thenReturn(Optional.of(original));

            AmendOrderCommand command = new AmendOrderCommand(0, 80_000L, "idem-amend-2");

            assertThatThrownBy(() -> orderService.amendOrder(userId, original.getId(), command))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("expirePendingOrders")
    class ExpirePendingOrders {

        @Test
        void shouldCancelAllPendingLimitOrdersAndSkipAlreadyProcessedOnes() {
            UUID okId = UUID.randomUUID();
            UUID racedId = UUID.randomUUID(); // 다른 스레드가 이미 처리해 findByIdForUpdate가 예외를 던지는 상황 가정
            when(orderRepository.findIdsByStatus(OrderStatusValue.PENDING))
                    .thenReturn(List.of(okId, racedId));

            OrderEntity okOrder = OrderEntity.place(userId, account.getId(), "005930",
                    OrderSideValue.BUY, OrderKindValue.LIMIT, 10, 70_000L, 700_105L, "idem-exp-1");
            when(orderRepository.findByIdForUpdate(okId)).thenReturn(Optional.of(okOrder));
            when(orderRepository.findByIdForUpdate(racedId)).thenReturn(Optional.empty());

            int cancelledCount = orderService.expirePendingOrders();

            assertThat(cancelledCount).isEqualTo(1);
            assertThat(okOrder.getStatus()).isEqualTo(OrderStatusValue.CANCELLED);
        }

        @Test
        void shouldReturnZeroWhenNoPendingOrders() {
            when(orderRepository.findIdsByStatus(OrderStatusValue.PENDING)).thenReturn(List.of());

            int cancelledCount = orderService.expirePendingOrders();

            assertThat(cancelledCount).isZero();
            verifyNoInteractions(accountService);
        }
    }
}
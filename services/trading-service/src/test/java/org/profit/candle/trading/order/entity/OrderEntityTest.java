package org.profit.candle.trading.order.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderEntityTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @Nested
    @DisplayName("place")
    class Place {

        @Test
        void shouldCreatePendingOrderForMarketOrderWithoutPrice() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.MARKET, 10, null, 0L, "idem-key-1");

            assertThat(order.pending()).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatusValue.PENDING);
            assertThat(order.getPriceKrw()).isNull();
            assertThat(order.getParentOrderId()).isNull();
        }

        @Test
        void shouldCreatePendingOrderForLimitOrderWithPrice() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.LIMIT, 10, 70_000L, 700_000L, "idem-key-2");

            assertThat(order.getPriceKrw()).isEqualTo(70_000L);
            assertThat(order.getReservedAmountKrw()).isEqualTo(700_000L);
        }

        @Test
        void shouldRejectZeroOrNegativeQuantity() {
            assertThatThrownBy(() -> OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.MARKET, 0, null, 0L, "idem-key-3"))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.INVALID_QUANTITY);
        }

        @Test
        void shouldRejectLimitOrderWithoutPrice() {
            assertThatThrownBy(() -> OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.LIMIT, 10, null, 0L, "idem-key-4"))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.LIMIT_ORDER_REQUIRES_PRICE);
        }

        @Test
        void shouldRejectMarketOrderWithPrice() {
            assertThatThrownBy(() -> OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.MARKET, 10, 70_000L, 0L, "idem-key-5"))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.MARKET_ORDER_MUST_NOT_HAVE_PRICE);
        }

        @Test
        void shouldRejectZeroOrNegativePrice() {
            assertThatThrownBy(() -> OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.LIMIT, 10, 0L, 0L, "idem-key-6"))
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.INVALID_PRICE);
        }
    }

    @Nested
    @DisplayName("placeWithParent")
    class PlaceWithParent {

        @Test
        void shouldLinkParentOrderId() {
            UUID parentId = UUID.randomUUID();

            OrderEntity amended = OrderEntity.placeWithParent(userId, accountId, "005930",
                    OrderSideValue.BUY, OrderKindValue.LIMIT, 5, 80_000L, 400_000L,
                    "idem-key-amend", parentId);

            assertThat(amended.getParentOrderId()).isEqualTo(parentId);
            assertThat(amended.pending()).isTrue();
        }
    }

    @Nested
    @DisplayName("fill")
    class Fill {

        @Test
        void shouldTransitionToFilledWhenPending() {
            OrderEntity order = pendingLimitOrder();

            order.fill();

            assertThat(order.getStatus()).isEqualTo(OrderStatusValue.FILLED);
            assertThat(order.getExecutedAt()).isNotNull();
        }

        @Test
        void shouldRejectFillWhenNotPending() {
            OrderEntity order = pendingLimitOrder();
            order.fill();

            assertThatThrownBy(order::fill)
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_PENDING);
        }
    }

    @Nested
    @DisplayName("markCancelled")
    class MarkCancelled {

        @Test
        void shouldCancelPendingLimitOrder() {
            OrderEntity order = pendingLimitOrder();

            order.markCancelled();

            assertThat(order.getStatus()).isEqualTo(OrderStatusValue.CANCELLED);
        }

        @Test
        void shouldRejectCancelWhenNotPending() {
            OrderEntity order = pendingLimitOrder();
            order.fill();

            assertThatThrownBy(order::markCancelled)
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.ORDER_NOT_PENDING);
        }

        @Test
        void shouldRejectCancelForMarketOrder() {
            OrderEntity order = OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                    OrderKindValue.MARKET, 10, null, 0L, "idem-key-market");

            assertThatThrownBy(order::markCancelled)
                    .isInstanceOf(OrderException.class)
                    .extracting(e -> ((OrderException) e).errorCode())
                    .isEqualTo(OrderErrorCode.MARKET_ORDER_CANNOT_BE_CANCELLED);
        }
    }

    private OrderEntity pendingLimitOrder() {
        return OrderEntity.place(userId, accountId, "005930", OrderSideValue.BUY,
                OrderKindValue.LIMIT, 10, 70_000L, 700_000L, "idem-key-" + UUID.randomUUID());
    }
}

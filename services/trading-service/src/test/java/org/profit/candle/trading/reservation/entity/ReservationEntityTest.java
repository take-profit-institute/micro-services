package org.profit.candle.trading.reservation.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationEntityTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final LocalDate scheduledDate = LocalDate.now().plusDays(1);

    @Nested
    @DisplayName("reserve — timing/orderKind 조합 검증")
    class ReserveTimingOrderKindValidation {

        @Test
        void shouldAllowOpenWithMarket() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, scheduledDate, 0L, "idem-1");

            assertThat(reservation.reserved()).isTrue();
        }

        @Test
        void shouldAllowOpenWithLimit() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.LIMIT,
                    10, 70_000L, scheduledDate, 700_105L, "idem-2");

            assertThat(reservation.getOrderKind()).isEqualTo(ReservationOrderKindValue.LIMIT);
        }

        @Test
        void shouldRejectOpenWithAfterHoursClose() {
            assertThatThrownBy(() -> ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN,
                    ReservationOrderKindValue.AFTER_HOURS_CLOSE, 10, null, scheduledDate, 0L, "idem-3"))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.TIMING_ORDER_KIND_MISMATCH);
        }

        @Test
        void shouldAllowTodayCloseWithAfterHoursClose() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.TODAY_CLOSE,
                    ReservationOrderKindValue.AFTER_HOURS_CLOSE, 10, null, scheduledDate, 0L, "idem-4");

            assertThat(reservation.getTiming()).isEqualTo(ReservationTimingValue.TODAY_CLOSE);
        }

        @Test
        void shouldRejectTodayCloseWithLimit() {
            assertThatThrownBy(() -> ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.TODAY_CLOSE,
                    ReservationOrderKindValue.LIMIT, 10, 70_000L, scheduledDate, 700_105L, "idem-5"))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.TIMING_ORDER_KIND_MISMATCH);
        }

        @Test
        void shouldAllowPrevCloseWithAfterHoursClose() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.SELL, ReservationTimingValue.PREV_CLOSE,
                    ReservationOrderKindValue.AFTER_HOURS_CLOSE, 10, null, scheduledDate, 0L, "idem-6");

            assertThat(reservation.getTiming()).isEqualTo(ReservationTimingValue.PREV_CLOSE);
        }
    }

    @Nested
    @DisplayName("reserve — 가격/수량 검증")
    class ReservePriceQuantityValidation {

        @Test
        void shouldRejectZeroOrNegativeQuantity() {
            assertThatThrownBy(() -> ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    0, null, scheduledDate, 0L, "idem-7"))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.INVALID_QUANTITY);
        }

        @Test
        void shouldRejectLimitWithoutPrice() {
            assertThatThrownBy(() -> ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.LIMIT,
                    10, null, scheduledDate, 0L, "idem-8"))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.LIMIT_RESERVATION_REQUIRES_PRICE);
        }

        @Test
        void shouldRejectNonLimitWithPrice() {
            assertThatThrownBy(() -> ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, 70_000L, scheduledDate, 0L, "idem-9"))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.NON_LIMIT_RESERVATION_MUST_NOT_HAVE_PRICE);
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        private ReservationEntity openLimitReservation() {
            return ReservationEntity.reserve(userId, accountId, "005930", ReservationSideValue.BUY,
                    ReservationTimingValue.OPEN, ReservationOrderKindValue.LIMIT, 10, 70_000L,
                    scheduledDate, 700_105L, "idem-status-" + UUID.randomUUID());
        }

        @Test
        void shouldTransitionToConvertingOnlyForOpenLimit() {
            ReservationEntity reservation = openLimitReservation();

            reservation.startConverting();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.CONVERTING);
        }

        @Test
        void shouldRejectStartConvertingForNonOpenLimitReservation() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, scheduledDate, 0L, "idem-status-2");

            assertThatThrownBy(reservation::startConverting)
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.NOT_CONVERTIBLE);
        }

        @Test
        void shouldRejectStartConvertingWhenNotReserved() {
            ReservationEntity reservation = openLimitReservation();
            reservation.startConverting();

            assertThatThrownBy(reservation::startConverting)
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_RESERVED);
        }

        @Test
        void shouldMarkConvertedOnlyFromConverting() {
            ReservationEntity reservation = openLimitReservation();
            reservation.startConverting();
            UUID orderId = UUID.randomUUID();

            reservation.markConverted(orderId);

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.EXECUTED);
            assertThat(reservation.getConvertedOrderId()).isEqualTo(orderId);
        }

        @Test
        void shouldRejectMarkConvertedWhenNotConverting() {
            ReservationEntity reservation = openLimitReservation();

            assertThatThrownBy(() -> reservation.markConverted(UUID.randomUUID()))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_CONVERTING);
        }

        @Test
        void shouldMarkExecutedOnlyFromReserved() {
            ReservationEntity reservation = openLimitReservation();

            reservation.markExecuted();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.EXECUTED);
        }

        @Test
        void shouldMarkCancelledOnlyFromReserved() {
            ReservationEntity reservation = openLimitReservation();

            reservation.markCancelled();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.CANCELLED);
        }

        @Test
        void shouldMarkFailedFromReservedOrConverting() {
            ReservationEntity fromReserved = openLimitReservation();
            fromReserved.markFailed();
            assertThat(fromReserved.getStatus()).isEqualTo(ReservationStatusValue.FAILED);

            ReservationEntity fromConverting = openLimitReservation();
            fromConverting.startConverting();
            fromConverting.markFailed();
            assertThat(fromConverting.getStatus()).isEqualTo(ReservationStatusValue.FAILED);
        }

        @Test
        void shouldRejectMarkFailedFromTerminalStatus() {
            ReservationEntity reservation = openLimitReservation();
            reservation.markCancelled();

            assertThatThrownBy(reservation::markFailed)
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_RESERVED);
        }

        @Test
        void shouldMarkExpiredOnlyFromReserved() {
            ReservationEntity reservation = openLimitReservation();

            reservation.markExpired();

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.EXPIRED);
        }

        @Test
        void shouldLinkParentReservationId() {
            ReservationEntity reservation = openLimitReservation();
            UUID parentId = UUID.randomUUID();

            reservation.linkParent(parentId);

            assertThat(reservation.getParentReservationId()).isEqualTo(parentId);
        }
    }
}

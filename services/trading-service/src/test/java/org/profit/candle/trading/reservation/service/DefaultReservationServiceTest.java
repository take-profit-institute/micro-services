package org.profit.candle.trading.reservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.client.MarketSessionClient;
import org.profit.candle.trading.reservation.dto.AmendReservationCommand;
import org.profit.candle.trading.reservation.dto.PlaceReservationCommand;
import org.profit.candle.trading.reservation.dto.ReservationCancelResult;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationSideValue;
import org.profit.candle.trading.reservation.entity.ReservationStatusValue;
import org.profit.candle.trading.reservation.entity.ReservationTimingValue;
import org.profit.candle.trading.reservation.event.ReservationOutboxOperations;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.support.event.OutboxWriter;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DefaultReservationService 흐름 테스트.
 *
 * <p>고정 시각: 2026-07-06 08:00 KST → 오늘=07-06, 내일=07-07.
 * placeReservation의 resolveAndValidateScheduledDate는 PREV_CLOSE를 제외하면 항상
 * "내일 이상"만 허용하므로, place 시점에는 scheduledDate가 오늘과 같아질 수 없다 —
 * 즉 deadlineValidator는 placeReservation에서 사실상 호출되지 않는다(회귀 방지 테스트로 고정).</p>
 */
@ExtendWith(MockitoExtension.class)
class DefaultReservationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock private ReservationRepository reservationRepository;
    @Mock private AccountService accountService;
    @Mock private OutboxWriter outboxWriter;
    @Mock private ReservationOutboxOperations outboxOperations;
    @Mock private ReservationDeadlineValidator deadlineValidator;
    @Mock private MarketSessionClient marketSessionClient;

    private DefaultReservationService reservationService;

    private final UUID userId = UUID.randomUUID();
    private AccountEntity account;
    private final Clock fixedClock = Clock.fixed(
            LocalDateTime.of(2026, 7, 6, 8, 0).atZone(KST).toInstant(), KST);
    private final LocalDate today = LocalDate.of(2026, 7, 6);
    private final LocalDate tomorrow = LocalDate.of(2026, 7, 7);

    @BeforeEach
    void setUp() {
        reservationService = new DefaultReservationService(reservationRepository, accountService,
                outboxWriter, outboxOperations, deadlineValidator, marketSessionClient, fixedClock);
        account = AccountEntity.create(userId);
    }

    @Nested
    @DisplayName("placeReservation")
    class PlaceReservation {

        @Test
        void shouldCreateOpenMarketReservationWithoutLockingBalance() {
            when(marketSessionClient.isTradingDay(tomorrow)).thenReturn(true);
            when(accountService.getAccount(userId)).thenReturn(account);
            when(reservationRepository.existsByAccountIdAndSymbolAndSideAndStatus(any(), anyString(), any(), any()))
                    .thenReturn(false);

            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, tomorrow, "idem-1");

            ReservationEntity reservation = reservationService.placeReservation(userId, command);

            assertThat(reservation.reserved()).isTrue();
            assertThat(reservation.getReservedAmountKrw()).isZero();
            verify(accountService, never()).lockBalance(any(), anyLong());
            verifyNoInteractions(deadlineValidator);
        }

        @Test
        void shouldLockBalanceForOpenLimitBuyReservation() {
            when(marketSessionClient.isTradingDay(tomorrow)).thenReturn(true);
            when(accountService.getAccount(userId)).thenReturn(account);
            when(reservationRepository.existsByAccountIdAndSymbolAndSideAndStatus(any(), anyString(), any(), any()))
                    .thenReturn(false);

            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.LIMIT,
                    10, 70_000L, tomorrow, "idem-2");

            reservationService.placeReservation(userId, command);

            // 700,000 * 0.00015 = 105 (반올림) → 700,105원 잠금
            verify(accountService).lockBalance(userId, 700_105L);
        }

        @Test
        void shouldNotLockBalanceForSellReservationEvenWithLimitPrice() {
            when(marketSessionClient.isTradingDay(tomorrow)).thenReturn(true);
            when(accountService.getAccount(userId)).thenReturn(account);
            when(reservationRepository.existsByAccountIdAndSymbolAndSideAndStatus(any(), anyString(), any(), any()))
                    .thenReturn(false);

            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.SELL, ReservationTimingValue.OPEN, ReservationOrderKindValue.LIMIT,
                    10, 70_000L, tomorrow, "idem-3");

            reservationService.placeReservation(userId, command);

            verify(accountService, never()).lockBalance(any(), anyLong());
        }

        @Test
        void shouldRejectDuplicatePendingReservationForSameSymbolAndSameSide() {
            when(marketSessionClient.isTradingDay(tomorrow)).thenReturn(true);
            when(accountService.getAccount(userId)).thenReturn(account);
            when(reservationRepository.existsByAccountIdAndSymbolAndSideAndStatus(
                    account.getId(), "005930", ReservationSideValue.BUY, ReservationStatusValue.RESERVED))
                    .thenReturn(true);

            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, tomorrow, "idem-4");

            assertThatThrownBy(() -> reservationService.placeReservation(userId, command))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.DUPLICATE_PENDING_RESERVATION);
        }

        @Test
        void shouldAllowSellReservationWhenReservedBuyExistsForSameSymbol() {
            // 매수 RESERVED가 있어도 매도는 별개 side라 막히면 안 된다 — UX 회귀 방지 테스트.
            // placeReservation은 command.side()(SELL)로만 조회하므로 BUY 쪽 stub은 실제로 호출되지
            // 않는다(Mockito strict stubbing에 unnecessary로 잡혀 제거함) — SELL false만으로 충분하다.
            when(marketSessionClient.isTradingDay(tomorrow)).thenReturn(true);
            when(accountService.getAccount(userId)).thenReturn(account);
            when(reservationRepository.existsByAccountIdAndSymbolAndSideAndStatus(
                    account.getId(), "005930", ReservationSideValue.SELL, ReservationStatusValue.RESERVED))
                    .thenReturn(false);

            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.SELL, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, tomorrow, "idem-4-sell");

            ReservationEntity reservation = reservationService.placeReservation(userId, command);

            assertThat(reservation.reserved()).isTrue();
            verify(reservationRepository).save(any(ReservationEntity.class));
        }

        @Test
        void shouldRejectWhenScheduledDateIsNotTradingDay() {
            when(marketSessionClient.isTradingDay(tomorrow)).thenReturn(false);

            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, tomorrow, "idem-5");

            assertThatThrownBy(() -> reservationService.placeReservation(userId, command))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.SCHEDULED_DATE_NOT_TRADING_DAY);
        }

        @Test
        void shouldFixPrevCloseScheduledDateToTomorrowRegardlessOfRequestedDate() {
            when(marketSessionClient.isTradingDay(tomorrow)).thenReturn(true);
            when(accountService.getAccount(userId)).thenReturn(account);
            when(reservationRepository.existsByAccountIdAndSymbolAndSideAndStatus(any(), anyString(), any(), any()))
                    .thenReturn(false);

            // scheduledDate를 null로 보내도(전일종가는 클라이언트 입력을 쓰지 않음) 내일로 고정된다.
            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.SELL, ReservationTimingValue.PREV_CLOSE,
                    ReservationOrderKindValue.AFTER_HOURS_CLOSE, 10, null, null, "idem-6");

            ReservationEntity reservation = reservationService.placeReservation(userId, command);

            assertThat(reservation.getScheduledDate()).isEqualTo(tomorrow);
        }

        @Test
        void shouldRejectOpenTimingWithoutScheduledDate() {
            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, null, "idem-7");

            assertThatThrownBy(() -> reservationService.placeReservation(userId, command))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.INVALID_SCHEDULED_DATE);
        }

        @Test
        void shouldRejectScheduledDateBeyondSevenDayWindow() {
            LocalDate tooFar = tomorrow.plusDays(7); // 내일부터 7일 이내(=tomorrow~tomorrow+6)를 벗어남

            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, tooFar, "idem-8");

            assertThatThrownBy(() -> reservationService.placeReservation(userId, command))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.INVALID_SCHEDULED_DATE);
        }

        @Test
        void shouldRejectScheduledDateBeforeTomorrow() {
            PlaceReservationCommand command = new PlaceReservationCommand("005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, today, "idem-9");

            assertThatThrownBy(() -> reservationService.placeReservation(userId, command))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.INVALID_SCHEDULED_DATE);
        }
    }

    @Nested
    @DisplayName("cancelReservation")
    class CancelReservation {

        @Test
        void shouldReleaseBalanceAndCancelWhenScheduledInFuture() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, account.getId(), "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.LIMIT,
                    10, 70_000L, tomorrow, 700_105L, "idem-cancel-1");
            when(reservationRepository.findByIdAndUserIdForUpdate(reservation.getId(), userId))
                    .thenReturn(Optional.of(reservation));

            ReservationCancelResult result = reservationService.cancelReservation(userId, reservation.getId());

            assertThat(result.reservation().getStatus()).isEqualTo(ReservationStatusValue.CANCELLED);
            assertThat(result.releasedAmount()).isEqualTo(700_105L);
            verify(accountService).releaseBalance(userId, 700_105L);
            // 미래 예약이라 오늘 마감 시간 검증 대상이 아니다.
            verifyNoInteractions(deadlineValidator);
        }

        @Test
        void shouldValidateDeadlineWhenScheduledDateIsTodayKst() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, account.getId(), "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, today, 0L, "idem-cancel-2");
            when(reservationRepository.findByIdAndUserIdForUpdate(reservation.getId(), userId))
                    .thenReturn(Optional.of(reservation));
            doThrow(new ReservationException(ReservationErrorCode.OPEN_DEADLINE_PASSED))
                    .when(deadlineValidator).requireBeforeDeadline(ReservationTimingValue.OPEN);

            assertThatThrownBy(() -> reservationService.cancelReservation(userId, reservation.getId()))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.OPEN_DEADLINE_PASSED);

            // 마감 검증 실패 시 취소가 진행되지 않아야 한다.
            assertThat(reservation.reserved()).isTrue();
        }

        @Test
        void shouldThrowNotFoundWhenReservationDoesNotBelongToUser() {
            UUID reservationId = UUID.randomUUID();
            when(reservationRepository.findByIdAndUserIdForUpdate(reservationId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.cancelReservation(userId, reservationId))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }

        @Test
        void shouldNotReleaseBalanceWhenReservedAmountIsZero() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, account.getId(), "005930",
                    ReservationSideValue.SELL, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, tomorrow, 0L, "idem-cancel-3");
            when(reservationRepository.findByIdAndUserIdForUpdate(reservation.getId(), userId))
                    .thenReturn(Optional.of(reservation));

            reservationService.cancelReservation(userId, reservation.getId());

            verify(accountService, never()).releaseBalance(any(), anyLong());
        }
    }

    @Nested
    @DisplayName("amendReservation")
    class Amend {

        @Test
        void shouldCancelOriginalAndCreateAmendedReservationLinkedByParentId() {
            ReservationEntity original = ReservationEntity.reserve(userId, account.getId(), "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.LIMIT,
                    10, 70_000L, tomorrow, 700_105L, "idem-orig");
            when(reservationRepository.findByIdAndUserIdForUpdate(original.getId(), userId))
                    .thenReturn(Optional.of(original));
            when(marketSessionClient.isTradingDay(tomorrow)).thenReturn(true);
            when(accountService.getAccount(userId)).thenReturn(account);

            AmendReservationCommand command = new AmendReservationCommand(
                    original.getId(), null, null, 5L, 80_000L, null, "idem-amend");

            ReservationEntity amended = reservationService.amendReservation(userId, command);

            assertThat(original.getStatus()).isEqualTo(ReservationStatusValue.CANCELLED);
            assertThat(amended.getParentReservationId()).isEqualTo(original.getId());
            assertThat(amended.getQuantity()).isEqualTo(5L);
            assertThat(amended.getPriceKrw()).isEqualTo(80_000L);
            // timing/scheduledDate는 null이라 원예약 값을 승계한다.
            assertThat(amended.getTiming()).isEqualTo(ReservationTimingValue.OPEN);
            assertThat(amended.getScheduledDate()).isEqualTo(tomorrow);
            verify(accountService).releaseBalance(userId, 700_105L);
        }

        @Test
        void shouldThrowNotFoundWhenOriginalReservationMissing() {
            UUID reservationId = UUID.randomUUID();
            when(reservationRepository.findByIdAndUserIdForUpdate(reservationId, userId))
                    .thenReturn(Optional.empty());

            AmendReservationCommand command = new AmendReservationCommand(
                    reservationId, null, null, 5L, 80_000L, null, "idem-amend-2");

            assertThatThrownBy(() -> reservationService.amendReservation(userId, command))
                    .isInstanceOf(ReservationException.class)
                    .extracting(e -> ((ReservationException) e).errorCode())
                    .isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }
    }
}
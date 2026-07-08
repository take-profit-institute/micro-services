package org.profit.candle.trading.reservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationSideValue;
import org.profit.candle.trading.reservation.entity.ReservationStatusValue;
import org.profit.candle.trading.reservation.entity.ReservationTimingValue;
import org.profit.candle.trading.reservation.event.ReservationOutboxOperations;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.support.event.OutboxWriter;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ReservationBatchExecutor 건별 트랜잭션 단위 테스트.
 * self-invocation 회피를 위해 분리된 클래스이므로, 각 public 메서드가 독립적으로
 * "락 재확인 → 상태 전이 → (필요 시) 잔고/Outbox" 순서를 지키는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationBatchExecutorTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private AccountService accountService;
    @Mock private OutboxWriter outboxWriter;
    @Mock private ReservationOutboxOperations outboxOperations;

    private ReservationBatchExecutor executor;

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        executor = new ReservationBatchExecutor(reservationRepository, accountService,
                outboxWriter, outboxOperations);
    }

    private ReservationEntity openLimitReservation(long reservedAmount) {
        return ReservationEntity.reserve(userId, accountId, "005930", ReservationSideValue.BUY,
                ReservationTimingValue.OPEN, ReservationOrderKindValue.LIMIT, 10, 70_000L,
                tomorrow, reservedAmount, "idem-" + UUID.randomUUID());
    }

    @Nested
    @DisplayName("checkReservedUnderLock")
    class CheckReservedUnderLock {

        @Test
        void shouldReturnTrueWhenReserved() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            assertThat(executor.checkReservedUnderLock(reservation.getId())).isTrue();
        }

        @Test
        void shouldReturnFalseWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(reservationRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

            assertThat(executor.checkReservedUnderLock(id)).isFalse();
        }

        @Test
        void shouldReturnFalseWhenAlreadyProcessedByAnotherTransaction() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            reservation.markCancelled();
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            assertThat(executor.checkReservedUnderLock(reservation.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("markFailedUnderLock")
    class MarkFailedUnderLock {

        @Test
        void shouldReleaseBalanceAndMarkFailedWhenReservedAmountIsPositive() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            executor.markFailedUnderLock(reservation.getId());

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.FAILED);
            verify(accountService).releaseBalance(userId, 700_105L);
        }

        @Test
        void shouldNotReleaseBalanceWhenReservedAmountIsZero() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.SELL, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, tomorrow, 0L, "idem-sell");
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            executor.markFailedUnderLock(reservation.getId());

            verify(accountService, never()).releaseBalance(any(), anyLong());
        }

        @Test
        void shouldBeNoOpWhenAlreadyNotReserved() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            reservation.markCancelled();
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            executor.markFailedUnderLock(reservation.getId());

            verifyNoInteractions(accountService);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.CANCELLED);
        }
    }

    @Nested
    @DisplayName("expireUnderLock")
    class ExpireUnderLock {

        @Test
        void shouldReleaseBalanceAndMarkExpired() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            boolean result = executor.expireUnderLock(reservation.getId());

            assertThat(result).isTrue();
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.EXPIRED);
            verify(accountService).releaseBalance(userId, 700_105L);
        }

        @Test
        void shouldReturnFalseWhenNotReserved() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            reservation.markCancelled();
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            assertThat(executor.expireUnderLock(reservation.getId())).isFalse();
            verifyNoInteractions(accountService);
        }
    }

    @Nested
    @DisplayName("processOpenLimitUnderLock")
    class ProcessOpenLimitUnderLock {

        @Test
        void shouldStartConvertingAndEmitReservationDueForOpenLimit() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            boolean result = executor.processOpenLimitUnderLock(reservation.getId());

            assertThat(result).isTrue();
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.CONVERTING);
            verify(outboxWriter).record(eq(outboxOperations), eq("ReservationDue"), anyString(), any());
        }

        @Test
        void shouldReturnFalseWhenOrderKindIsNotLimit() {
            ReservationEntity reservation = ReservationEntity.reserve(userId, accountId, "005930",
                    ReservationSideValue.BUY, ReservationTimingValue.OPEN, ReservationOrderKindValue.MARKET,
                    10, null, tomorrow, 0L, "idem-market");
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            assertThat(executor.processOpenLimitUnderLock(reservation.getId())).isFalse();
            assertThat(reservation.reserved()).isTrue();
            verifyNoInteractions(outboxWriter);
        }

        @Test
        void shouldReturnFalseWhenAlreadyProcessed() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            reservation.startConverting();
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            assertThat(executor.processOpenLimitUnderLock(reservation.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("failConvertingUnderLock")
    class FailConvertingUnderLock {

        @Test
        void shouldReleaseBalanceAndMarkFailedWhenStuckInConverting() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            reservation.startConverting();
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            boolean result = executor.failConvertingUnderLock(reservation.getId());

            assertThat(result).isTrue();
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.FAILED);
            verify(accountService).releaseBalance(userId, 700_105L);
        }

        @Test
        void shouldReturnFalseWhenNotInConverting() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            assertThat(executor.failConvertingUnderLock(reservation.getId())).isFalse();
            verifyNoInteractions(accountService);
        }
    }

    @Nested
    @DisplayName("executeUnderLock")
    class ExecuteUnderLock {

        @Test
        void shouldMarkExecutedAndEmitReservationExecuted() {
            ReservationEntity reservation = openLimitReservation(700_105L);
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            boolean result = executor.executeUnderLock(reservation.getId(), 70_000L, 700_105L);

            assertThat(result).isTrue();
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatusValue.EXECUTED);
            verify(outboxWriter).record(eq(outboxOperations), eq("ReservationExecuted"), anyString(), any());
        }

        @Test
        void shouldReturnFalseWhenNotReservedAtReacquisitionTime() {
            // 종가 체결 4단계 — 락 재획득 시점에 이미 다른 트랜잭션이 상태를 바꾼 경우.
            ReservationEntity reservation = openLimitReservation(700_105L);
            reservation.markCancelled();
            when(reservationRepository.findByIdForUpdate(reservation.getId()))
                    .thenReturn(Optional.of(reservation));

            boolean result = executor.executeUnderLock(reservation.getId(), 70_000L, 700_105L);

            assertThat(result).isFalse();
            verifyNoInteractions(outboxWriter);
        }
    }

    @Nested
    @DisplayName("lockBalanceInNewTransaction / releaseBalanceInNewTransaction")
    class BalanceInNewTransaction {

        @Test
        void shouldDelegateLockBalanceToAccountService() {
            executor.lockBalanceInNewTransaction(userId, 500_000L);

            verify(accountService).lockBalance(userId, 500_000L);
        }

        @Test
        void shouldDelegateReleaseBalanceToAccountService() {
            executor.releaseBalanceInNewTransaction(userId, 500_000L);

            verify(accountService).releaseBalance(userId, 500_000L);
        }

        @Test
        void shouldSkipReleaseWhenAmountIsZeroOrUserIdIsNull() {
            executor.releaseBalanceInNewTransaction(userId, 0L);
            executor.releaseBalanceInNewTransaction(null, 500_000L);

            verifyNoInteractions(accountService);
        }
    }
}

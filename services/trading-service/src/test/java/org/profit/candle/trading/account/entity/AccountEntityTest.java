package org.profit.candle.trading.account.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AccountEntity 상태 전이/불변식 단위 테스트.
 * Spring 컨텍스트 없이 순수 도메인 로직만 검증한다 (컨벤션 12장: Entity 규칙은 단위 테스트).
 */
class AccountEntityTest {

    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void shouldCreateActiveAccountWithInitialSeedMoney() {
            AccountEntity account = AccountEntity.create(userId);

            assertThat(account.getUserId()).isEqualTo(userId);
            assertThat(account.active()).isTrue();
            assertThat(account.getCashKrw()).isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW);
            assertThat(account.getLockedKrw()).isZero();
            assertThat(account.availableKrw()).isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW);
        }
    }

    @Nested
    @DisplayName("availableKrw")
    class AvailableKrw {

        @Test
        void shouldReturnCashMinusLocked() {
            AccountEntity account = AccountEntity.create(userId);
            account.lock(10_000_000L);

            assertThat(account.availableKrw())
                    .isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW - 10_000_000L);
        }
    }

    @Nested
    @DisplayName("lock")
    class Lock {

        @Test
        void shouldIncreaseLockedKrwWhenAmountIsPositive() {
            AccountEntity account = AccountEntity.create(userId);

            account.lock(5_000_000L);

            assertThat(account.getLockedKrw()).isEqualTo(5_000_000L);
        }

        @Test
        void shouldRejectLockWhenAmountIsZero() {
            AccountEntity account = AccountEntity.create(userId);

            assertThatThrownBy(() -> account.lock(0L))
                    .isInstanceOf(AccountException.class)
                    .extracting(e -> ((AccountException) e).errorCode())
                    .isEqualTo(AccountErrorCode.INVALID_LOCK_AMOUNT);
        }

        @Test
        void shouldRejectLockWhenAmountIsNegative() {
            AccountEntity account = AccountEntity.create(userId);

            assertThatThrownBy(() -> account.lock(-1L))
                    .isInstanceOf(AccountException.class)
                    .extracting(e -> ((AccountException) e).errorCode())
                    .isEqualTo(AccountErrorCode.INVALID_LOCK_AMOUNT);
        }
    }

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        void shouldDecreaseLockedKrwWhenAmountIsWithinLockedBalance() {
            AccountEntity account = AccountEntity.create(userId);
            account.lock(5_000_000L);

            account.release(3_000_000L);

            assertThat(account.getLockedKrw()).isEqualTo(2_000_000L);
        }

        @Test
        void shouldRejectReleaseWhenAmountExceedsLockedBalance() {
            AccountEntity account = AccountEntity.create(userId);
            account.lock(1_000_000L);

            assertThatThrownBy(() -> account.release(2_000_000L))
                    .isInstanceOf(AccountException.class)
                    .extracting(e -> ((AccountException) e).errorCode())
                    .isEqualTo(AccountErrorCode.INSUFFICIENT_LOCKED_BALANCE);
        }

        @Test
        void shouldRejectReleaseWhenAmountIsZeroOrNegative() {
            AccountEntity account = AccountEntity.create(userId);
            account.lock(1_000_000L);

            assertThatThrownBy(() -> account.release(0L))
                    .isInstanceOf(AccountException.class)
                    .extracting(e -> ((AccountException) e).errorCode())
                    .isEqualTo(AccountErrorCode.INVALID_RELEASE_AMOUNT);
        }
    }

    @Nested
    @DisplayName("settleBuy")
    class SettleBuy {

        @Test
        void shouldReleaseLockedAmountAndDeductCashOnBuySettlement() {
            AccountEntity account = AccountEntity.create(userId);
            account.lock(1_015_000L); // 1,000,000원 매수 + 수수료 15,000원 가정

            account.settleBuy(1_015_000L, 1_015_000L);

            assertThat(account.getLockedKrw()).isZero();
            assertThat(account.getCashKrw())
                    .isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW - 1_015_000L);
        }

        @Test
        void shouldRejectSettleBuyWhenSettledAmountExceedsCashBalance() {
            AccountEntity account = AccountEntity.create(userId);
            long tooMuch = AccountEntity.INITIAL_SEED_MONEY_KRW + 1;
            account.lock(tooMuch);

            assertThatThrownBy(() -> account.settleBuy(tooMuch, tooMuch))
                    .isInstanceOf(AccountException.class)
                    .extracting(e -> ((AccountException) e).errorCode())
                    .isEqualTo(AccountErrorCode.INSUFFICIENT_CASH_BALANCE);
        }

        @Test
        void shouldSkipReleaseWhenLockedAmountIsZero() {
            // recordReservationFill 같은 경로에서 lockedAmount=0으로 호출될 수 있다.
            AccountEntity account = AccountEntity.create(userId);

            account.settleBuy(0L, 1_000_000L);

            assertThat(account.getLockedKrw()).isZero();
            assertThat(account.getCashKrw())
                    .isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW - 1_000_000L);
        }
    }

    @Nested
    @DisplayName("settleSell")
    class SettleSell {

        @Test
        void shouldIncreaseCashKrwWithoutTouchingLockedBalance() {
            AccountEntity account = AccountEntity.create(userId);

            account.settleSell(2_000_000L);

            assertThat(account.getCashKrw())
                    .isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW + 2_000_000L);
            assertThat(account.getLockedKrw()).isZero();
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        void shouldMarkAccountInactive() {
            AccountEntity account = AccountEntity.create(userId);

            account.deactivate();

            assertThat(account.active()).isFalse();
            assertThat(account.getStatus()).isEqualTo(AccountStatusValue.INACTIVE);
        }
    }
}

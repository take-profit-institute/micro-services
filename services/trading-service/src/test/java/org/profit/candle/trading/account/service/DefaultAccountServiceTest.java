package org.profit.candle.trading.account.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;
import org.profit.candle.trading.account.repository.AccountRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DefaultAccountService 흐름 테스트 — repository는 mock으로 대체한다
 * (컨벤션 12장: service 흐름은 repository/client fake 또는 mock 테스트).
 *
 * 실제 비관적 락/동시성 검증은 통합 테스트(Testcontainers) 영역이며, 여기서는
 * loadOrCreateForUpdate의 분기(존재/미존재)와 도메인 예외 전파만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DefaultAccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private DefaultAccountService accountService;

    private UUID userId;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        accountService = new DefaultAccountService(accountRepository);
        userId = UUID.randomUUID();
        account = AccountEntity.create(userId);
    }

    @Nested
    @DisplayName("getAccount")
    class GetAccount {

        @Test
        void shouldReturnAccountWhenExists() {
            when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

            AccountEntity result = accountService.getAccount(userId);

            assertThat(result).isEqualTo(account);
        }

        @Test
        void shouldThrowAccountNotFoundWhenAccountDoesNotExist() {
            when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccount(userId))
                    .isInstanceOf(AccountException.class)
                    .extracting(e -> ((AccountException) e).errorCode())
                    .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("lockBalance")
    class LockBalance {

        @Test
        void shouldLockBalanceWhenAvailableIsSufficient() {
            when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

            accountService.lockBalance(userId, 10_000_000L);

            assertThat(account.getLockedKrw()).isEqualTo(10_000_000L);
        }

        @Test
        void shouldRejectLockBalanceWhenAvailableIsInsufficient() {
            long tooMuch = AccountEntity.INITIAL_SEED_MONEY_KRW + 1;
            when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.lockBalance(userId, tooMuch))
                    .isInstanceOf(AccountException.class)
                    .extracting(e -> ((AccountException) e).errorCode())
                    .isEqualTo(AccountErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);

            assertThat(account.getLockedKrw()).isZero();
        }

        @Test
        void shouldCreateAccountWithFallbackWhenNotFoundOnFirstAccess() {
            // UserCreated 이벤트 처리가 아직 안 끝난 레이스 상황을 방어하는 fallback 경로.
            when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());
            when(accountRepository.save(any(AccountEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            accountService.lockBalance(userId, 1_000_000L);

            ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
            verify(accountRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(userId);
            assertThat(captor.getValue().getLockedKrw()).isEqualTo(1_000_000L);
        }
    }

    @Nested
    @DisplayName("releaseBalance")
    class ReleaseBalance {

        @Test
        void shouldReleaseLockedBalance() {
            account.lock(5_000_000L);
            when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

            accountService.releaseBalance(userId, 2_000_000L);

            assertThat(account.getLockedKrw()).isEqualTo(3_000_000L);
        }
    }

    @Nested
    @DisplayName("settleBuy")
    class SettleBuy {

        @Test
        void shouldSettleBuyAgainstLoadedAccount() {
            account.lock(1_015_000L);
            when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

            accountService.settleBuy(userId, 1_015_000L, 1_015_000L);

            assertThat(account.getLockedKrw()).isZero();
            assertThat(account.getCashKrw())
                    .isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW - 1_015_000L);
        }
    }

    @Nested
    @DisplayName("settleSell")
    class SettleSell {

        @Test
        void shouldSettleSellAgainstLoadedAccount() {
            when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

            accountService.settleSell(userId, 2_000_000L);

            assertThat(account.getCashKrw())
                    .isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW + 2_000_000L);
        }
    }
}

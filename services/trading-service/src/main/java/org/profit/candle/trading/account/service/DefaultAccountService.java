package org.profit.candle.trading.account.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;
import org.profit.candle.trading.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultAccountService implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public AccountEntity getAccount(UUID userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Override
    @Transactional
    public void lockBalance(UUID userId, long amount) {
        AccountEntity account = loadOrCreateForUpdate(userId);
        if (account.availableKrw() < amount) {
            throw new AccountException(AccountErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
        }
        account.lock(amount);
    }

    @Override
    @Transactional
    public void releaseBalance(UUID userId, long amount) {
        AccountEntity account = loadOrCreateForUpdate(userId);
        account.release(amount);
    }

    /**
     * 잔고 변경 직전 락을 걸고 조회한다. 없으면 생성한다.
     * 계좌는 ACC-001(회원가입 완료 시 자동 생성)에 따라 보통 이미 존재해야 하지만,
     * UserCreated 이벤트 처리가 아직 완료되지 않은 레이스를 방어하기 위해
     * 첫 액세스 시점에 생성하는 fallback을 유지한다.
     */
    private AccountEntity loadOrCreateForUpdate(UUID userId) {
        return accountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> accountRepository.save(AccountEntity.create(userId)));
    }
}
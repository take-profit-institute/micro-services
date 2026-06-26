package org.profit.candle.trading.account.service;

import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.entity.AccountBalanceEntity;
import org.profit.candle.trading.account.repository.AccountBalanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultAccountService implements AccountService {

    private final AccountBalanceRepository balanceRepository;

    @Value("${trading.starting-cash:10000000}")
    private long startingCash;

    @Override
    public AccountBalanceEntity getBalance(String actorId) {
        // 단순 조회 — 락 불필요
        return balanceRepository.findById(actorId)
                .orElseGet(() -> balanceRepository.save(new AccountBalanceEntity(actorId, startingCash, 0)));
    }

    @Override
    public void reserveBalance(String actorId, long amount) {
        AccountBalanceEntity balance = loadOrCreateForUpdate(actorId);
        if (balance.availableCash() < amount) {
            throw Status.FAILED_PRECONDITION.withDescription("가용 금액이 부족합니다").asRuntimeException();
        }
        balance.reserve(amount);
        balanceRepository.save(balance);
    }

    @Override
    public void releaseBalance(String actorId, long amount) {
        AccountBalanceEntity balance = loadOrCreateForUpdate(actorId);
        balance.releaseReservation(amount);
        balanceRepository.save(balance);
    }

    /**
     * 잔고 변경 직전 락을 걸고 조회한다. 없으면 생성한다.
     * 동시에 같은 actorId로 최초 생성이 시도되면 unique 제약 충돌이 날 수 있으므로,
     * 충돌 시 패자는 승자가 commit한 row를 락 걸고 재조회한다.
     */
    private AccountBalanceEntity loadOrCreateForUpdate(String actorId) {
        return balanceRepository.findByIdForUpdate(actorId)
                .orElseGet(() -> createOrFetchExisting(actorId));
    }

    private AccountBalanceEntity createOrFetchExisting(String actorId) {
        try {
            return balanceRepository.save(new AccountBalanceEntity(actorId, startingCash, 0));
        } catch (DataIntegrityViolationException race) {
            // 동시 요청이 먼저 commit — PK 충돌. 승자의 row를 락 걸고 재조회한다.
            return balanceRepository.findByIdForUpdate(actorId)
                    .orElseThrow(() -> race);
        }
    }
}
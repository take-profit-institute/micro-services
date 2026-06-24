package org.profit.candle.trading.account.service;

import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.entity.AccountBalanceEntity;
import org.profit.candle.trading.account.repository.AccountBalanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultAccountService implements AccountService {

    private final AccountBalanceRepository balanceRepository;

    @Value("${trading.starting-cash:10000000}")
    private long startingCash;

    @Override
    public AccountBalanceEntity getBalance(String actorId) {
        return loadOrCreateBalance(actorId);
    }

    @Override
    public void reserveBalance(String actorId, long amount) {
        AccountBalanceEntity balance = loadOrCreateBalance(actorId);
        if (balance.availableCash() < amount) {
            throw Status.FAILED_PRECONDITION.withDescription("가용 금액이 부족합니다").asRuntimeException();
        }
        balance.reserve(amount);
        balanceRepository.save(balance);
    }

    @Override
    public void releaseBalance(String actorId, long amount) {
        AccountBalanceEntity balance = loadOrCreateBalance(actorId);
        balance.releaseReservation(amount);
        balanceRepository.save(balance);
    }

    private AccountBalanceEntity loadOrCreateBalance(String actorId) {
        return balanceRepository.findById(actorId)
                .orElseGet(() -> balanceRepository.save(new AccountBalanceEntity(actorId, startingCash, 0)));
    }
}
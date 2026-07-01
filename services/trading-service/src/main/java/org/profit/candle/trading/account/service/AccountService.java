package org.profit.candle.trading.account.service;

import org.profit.candle.trading.account.entity.AccountEntity;

import java.util.UUID;

/**
 * Account 도메인 업무 서비스. Order 등 다른 도메인은 이 인터페이스로만 잔고를 조작한다.
 */
public interface AccountService {

    /** actorId의 잔고를 조회한다. 없으면 초기 잔고로 생성한다. */
    AccountEntity getAccount(UUID userId);

    /** amount만큼 가용 잔고를 예약(reserve)한다. 가용 금액 부족 시 FAILED_PRECONDITION을 던진다. */
    void lockBalance(UUID actorId, long amount);

    /** amount만큼 예약된 잔고를 해제한다. */
    void releaseBalance(UUID actorId, long amount);

    /**
     * 매수 체결 시 잔고 정산 (EXE-009/010). lockedAmount(주문 접수 시 잠갔던 금액)를
     * 반환하고, settledAmount(실제 net_amount)를 현금잔고에서 차감한다.
     * 락을 걸고 처리해 동시 체결/취소 요청과의 경합을 막는다.
     */
    void settleBuy(UUID userId, long lockedAmount, long settledAmount);

    /** 매도 체결 시 잔고 정산. settledAmount를 현금잔고에 더한다. */
    void settleSell(UUID userId, long settledAmount);
}
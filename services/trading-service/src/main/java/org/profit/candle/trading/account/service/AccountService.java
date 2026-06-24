package org.profit.candle.trading.account.service;

import org.profit.candle.trading.account.entity.AccountBalanceEntity;

/**
 * Account 도메인 업무 서비스. Order 등 다른 도메인은 이 인터페이스로만 잔고를 조작한다.
 */
public interface AccountService {

    /** actorId의 잔고를 조회한다. 없으면 초기 잔고로 생성한다. */
    AccountBalanceEntity getBalance(String actorId);

    /** amount만큼 가용 잔고를 예약(reserve)한다. 가용 금액 부족 시 FAILED_PRECONDITION을 던진다. */
    void reserveBalance(String actorId, long amount);

    /** amount만큼 예약된 잔고를 해제한다. */
    void releaseBalance(String actorId, long amount);
}
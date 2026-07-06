package org.profit.candle.trading.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;

import java.time.Instant;
import java.util.UUID;

/**
 * 계좌 마스터 엔티티.
 * 사용자 1인당 1개씩 존재하며, 현금 잔고와 잠금 금액을 보유한다.
 * 가용금액(cash_krw - locked_krw)은 저장하지 않고 조회 시점에 계산한다.
 *
 * <p>잔고 변경은 항상 {@code loadOrCreateForUpdate} 패턴 + 비관적 락을 통해
 * {@code AccountService}에서만 수행한다. 이 엔티티는 상태 보관 역할만 한다.</p>
 */
@Entity
@Table(schema = "account", name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntity {
    @Id
    private UUID id;

    /** Auth/User 서비스 소유, 값만 복사. 1인당 계좌 1개 제약(UNIQUE)은 DB 레벨에서 보장. */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatusValue status;

    @Column(name = "cash_krw", nullable = false)
    private long cashKrw;

    @Column(name = "locked_krw", nullable = false)
    private long lockedKrw;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private AccountEntity(UUID id, UUID userId, AccountStatusValue status, long cashKrw, long lockedKrw,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.cashKrw = cashKrw;
        this.lockedKrw = lockedKrw;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 초기 시드머니. 금액 단위는 원(KRW). */
    public static final long INITIAL_SEED_MONEY_KRW = 100_000_000L;

    /**
     * 신규 계좌 생성. 계좌 생성 시 초기 시드머니(1억원)를 cash_krw에 즉시 반영한다.
     *
     * <p><b>주의</b>: account_deposits(입금이력) 테이블이 아직 마이그레이션되지 않아,
     * 이 시드머니 지급의 근거(deposit_type=INITIAL)를 별도로 기록하지 못하는 상태다.
     * DEP-001(입금 이력 생성)이 별도 이슈로 들어올 때까지는, cash_krw 값만으로는
     * "왜 1억원이 들어있는지"를 추적할 수 없다는 점을 인지하고 있어야 한다.</p>
     */
    public static AccountEntity create(UUID userId) {
        Instant now = Instant.now();
        return new AccountEntity(UUID.randomUUID(), userId, AccountStatusValue.ACTIVE,
                INITIAL_SEED_MONEY_KRW, 0L, now, now);
    }

    /** 가용 가능 금액 = 현금잔고 - 잠금금액. 저장하지 않고 매번 계산한다. */
    public long availableKrw() {
        return cashKrw - lockedKrw;
    }

    public boolean active() {
        return status == AccountStatusValue.ACTIVE;
    }

    /**
     * 매수 주문 접수 시 잠금금액 증가 (ORD-006).
     * 가용 가능 금액 검증은 호출 측({@code AccountService})의 책임이다 —
     * 이 메서드는 검증된 amount를 받아 상태만 반영한다.
     */
    public void lock(long amount) {
        if (amount <= 0) {
            throw new AccountException(AccountErrorCode.INVALID_LOCK_AMOUNT);
        }
        this.lockedKrw += amount;
        this.updatedAt = Instant.now();
    }

    /** 주문 취소/만료/실패 시 잠금금액 즉시 반환 (CAN-004). */
    public void release(long amount) {
        if (amount <= 0) {
            throw new AccountException(AccountErrorCode.INVALID_RELEASE_AMOUNT);
        }
        if (amount > this.lockedKrw) {
            throw new AccountException(AccountErrorCode.INSUFFICIENT_LOCKED_BALANCE);
        }
        this.lockedKrw -= amount;
        this.updatedAt = Instant.now();
    }

    /**
     * 체결 완료 시 현금잔고 차감 + 잠금금액 감소 (EXE-010).
     * lockedAmount는 주문 접수 시 잠갔던 금액(reserved_amount_krw), settledAmount는
     * 실제 체결로 빠져나가는 금액(net_amount_krw, 수수료 포함)이며 시세 변동이 없는
     * 한 보통 같지만, 향후 부분 체결/수수료 차이를 대비해 분리해 받는다.
     */
    public void settleBuy(long lockedAmount, long settledAmount) {
        if (lockedAmount > 0) {
            release(lockedAmount);
        }
        if (settledAmount > this.cashKrw) {
            throw new AccountException(AccountErrorCode.INSUFFICIENT_CASH_BALANCE);
        }
        this.cashKrw -= settledAmount;
        this.updatedAt = Instant.now();
    }

    /** 매도 체결 시 현금잔고 증가. 매도는 잠금금액을 쓰지 않으므로 release 호출 불필요. */
    public void settleSell(long settledAmount) {
        this.cashKrw += settledAmount;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status = AccountStatusValue.INACTIVE;
        this.updatedAt = Instant.now();
    }
}

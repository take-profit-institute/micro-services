package org.profit.candle.trading.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_balances")
public class AccountBalanceEntity {

    @Id
    @Column(name = "user_id", length = 120)
    private String userId;

    @Column(nullable = false)
    private long cash;

    @Column(name = "reserved_balance", nullable = false)
    private long reservedBalance;

    protected AccountBalanceEntity() {}

    public AccountBalanceEntity(String userId, long cash, long reservedBalance) {
        this.userId = userId;
        this.cash = cash;
        this.reservedBalance = reservedBalance;
    }

    public String userId() { return userId; }
    public long cash() { return cash; }
    public long reservedBalance() { return reservedBalance; }
    public long availableCash() { return cash - reservedBalance; }

    public void reserve(long amount) {
        this.reservedBalance += amount;
    }

    public void releaseReservation(long amount) {
        this.reservedBalance -= amount;
    }

    public void debit(long amount) {
        this.cash -= amount;
    }

    public void credit(long amount) {
        this.cash += amount;
    }
}

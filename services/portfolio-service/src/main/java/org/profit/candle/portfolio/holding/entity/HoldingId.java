package org.profit.candle.portfolio.holding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class HoldingId implements Serializable {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(length = 20, nullable = false)
    private String symbol;

    protected HoldingId() {}

    public HoldingId(String userId, String symbol) {
        this.userId = userId;
        this.symbol = symbol;
    }

    public String userId() { return userId; }
    public String symbol() { return symbol; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoldingId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, symbol); }
}

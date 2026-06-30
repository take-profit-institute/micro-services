package org.profit.candle.portfolio.holding.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.profit.candle.portfolio.holding.entity.SellOutcome;

import java.time.Instant;

/**
 * 청산(매도)된 거래 1건의 실현 원장. 승률 / 평균 보유기간 통계의 원천 데이터.
 */
@Entity
@Getter
@Accessors(fluent = true)
@Table(name = "portfolio_realized_trades")
public class RealizedTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(length = 20, nullable = false)
    private String symbol;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "entry_price", nullable = false)
    private long entryPrice;

    @Column(name = "exit_price", nullable = false)
    private long exitPrice;

    @Column(name = "realized_profit", nullable = false)
    private long realizedProfit;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RealizedTradeEntity() {}

    public RealizedTradeEntity(String userId, String symbol, SellOutcome outcome) {
        this.userId = userId;
        this.symbol = symbol;
        this.quantity = outcome.quantity();
        this.entryPrice = outcome.entryPrice();
        this.exitPrice = outcome.exitPrice();
        this.realizedProfit = outcome.realizedProfit();
        this.openedAt = outcome.openedAt();
        this.closedAt = outcome.closedAt();
        this.createdAt = Instant.now();
    }
}

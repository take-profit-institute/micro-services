package org.profit.candle.trading.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.profit.candle.trading.domain.OrderKindValue;
import org.profit.candle.trading.domain.OrderSideValue;
import org.profit.candle.trading.domain.OrderStatusValue;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "user_id", nullable = false, length = 120)
    private String userId;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderSideValue side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderKindValue kind;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatusValue status;

    @Column(name = "parent_order_id", length = 40)
    private String parentOrderId;

    @Column(name = "reserved_amount", nullable = false)
    private long reservedAmount;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected OrderEntity() {}

    public OrderEntity(String id, String userId, String symbol, OrderSideValue side, OrderKindValue kind,
                       long quantity, long price, OrderStatusValue status, String parentOrderId, long reservedAmount) {
        this.id = id;
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.kind = kind;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
        this.parentOrderId = parentOrderId;
        this.reservedAmount = reservedAmount;
    }

    public String id() { return id; }
    public String userId() { return userId; }
    public String symbol() { return symbol; }
    public OrderSideValue side() { return side; }
    public OrderKindValue kind() { return kind; }
    public long quantity() { return quantity; }
    public long price() { return price; }
    public OrderStatusValue status() { return status; }
    public String parentOrderId() { return parentOrderId; }
    public long reservedAmount() { return reservedAmount; }
    public Instant createdAt() { return createdAt; }

    public void markCancelled() { this.status = OrderStatusValue.CANCELLED; }
}

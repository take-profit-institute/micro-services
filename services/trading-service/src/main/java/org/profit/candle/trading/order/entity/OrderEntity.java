package org.profit.candle.trading.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.profit.candle.trading.order.exception.OrderErrorCode;
import org.profit.candle.trading.order.exception.OrderException;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "order_svc", name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** account 도메인 소유, 값만 복사. */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Market 도메인 소유, 값만 복사. */
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private OrderSideValue side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_kind", nullable = false, length = 10)
    private OrderKindValue orderKind;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    /** LIMIT일 때만 값 존재. */
    @Column(name = "price_krw")
    private Long priceKrw;

    /** 이 주문이 잠근 금액(수수료 포함). BUY만 0보다 큼, SELL은 잔고를 잠그지 않으므로 0. */
    @Column(name = "reserved_amount_krw", nullable = false)
    private long reservedAmountKrw;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private OrderStatusValue status;

    /** 정정 시 원 주문 참조 (CAN-008). */
    @Column(name = "parent_order_id")
    private UUID parentOrderId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private OrderEntity(UUID id, UUID userId, UUID accountId, String symbol, OrderSideValue side,
                        OrderKindValue orderKind, long quantity, Long priceKrw, long reservedAmountKrw,
                        OrderStatusValue status, UUID parentOrderId, String idempotencyKey,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.orderKind = orderKind;
        this.quantity = quantity;
        this.priceKrw = priceKrw;
        this.reservedAmountKrw = reservedAmountKrw;
        this.status = status;
        this.parentOrderId = parentOrderId;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 신규 즉시 주문 생성. (ORD-002/003)
     * 가용 가능 금액 검증과 AccountService.lockBalance 호출은 호출 측({@code OrderService})의
     * 책임이다 — 이 팩토리는 검증된 reservedAmountKrw를 받아 PENDING 상태로 생성만 한다.
     *
     * <p>{@code order_kind}별 {@code price_krw} 존재 여부 불변식은 DB CHECK 제약과 동일하게
     * 여기서도 한 번 더 검사한다 — Entity는 항상 유지되어야 하는 상태 규칙을 스스로 지킨다.</p>
     */
    public static OrderEntity place(UUID userId, UUID accountId, String symbol, OrderSideValue side,
                                    OrderKindValue orderKind, long quantity, Long priceKrw,
                                    long reservedAmountKrw, String idempotencyKey) {
        if (quantity <= 0) {
            throw new OrderException(OrderErrorCode.INVALID_QUANTITY);
        }
        if (orderKind == OrderKindValue.LIMIT && priceKrw == null) {
            throw new OrderException(OrderErrorCode.LIMIT_ORDER_REQUIRES_PRICE);
        }
        if (orderKind == OrderKindValue.MARKET && priceKrw != null) {
            throw new OrderException(OrderErrorCode.MARKET_ORDER_MUST_NOT_HAVE_PRICE);
        }
        if (priceKrw != null && priceKrw <= 0) {
            throw new OrderException(OrderErrorCode.INVALID_PRICE);
        }

        Instant now = Instant.now();
        return new OrderEntity(UUID.randomUUID(), userId, accountId, symbol, side, orderKind,
                quantity, priceKrw, reservedAmountKrw, OrderStatusValue.PENDING, null, idempotencyKey,
                now, now);
    }

    public boolean pending() {
        return status == OrderStatusValue.PENDING;
    }

    /** 체결 처리 (EXE-001/002 — 향후 작업에서 호출). 상태 전이만 책임진다. */
    public void fill() {
        if (!pending()) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_PENDING);
        }
        this.status = OrderStatusValue.FILLED;
        this.executedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 취소 처리 (CAN-001/002/003). PENDING 상태인 지정가 주문만 취소 가능하다.
     * reserved_balance 반환(AccountService.releaseBalance 호출)은 호출 측의 책임이다.
     */
    public void markCancelled() {
        if (!pending()) {
            throw new OrderException(OrderErrorCode.ORDER_NOT_PENDING);
        }
        if (orderKind != OrderKindValue.LIMIT) {
            throw new OrderException(OrderErrorCode.MARKET_ORDER_CANNOT_BE_CANCELLED);
        }
        this.status = OrderStatusValue.CANCELLED;
        this.updatedAt = Instant.now();
    }
}

package org.profit.candle.trading.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "order_svc", name = "executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "executed_price_krw", nullable = false)
    private long executedPriceKrw;

    @Column(name = "executed_quantity", nullable = false)
    private long executedQuantity;

    @Column(name = "fee_krw", nullable = false)
    private long feeKrw;

    @Column(name = "tax_krw", nullable = false)
    private long taxKrw;

    @Column(name = "net_amount_krw", nullable = false)
    private long netAmountKrw;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    private ExecutionEntity(UUID orderId, long executedPriceKrw, long executedQuantity,
                            long feeKrw, long taxKrw, long netAmountKrw, Instant executedAt) {
        this.orderId = orderId;
        this.executedPriceKrw = executedPriceKrw;
        this.executedQuantity = executedQuantity;
        this.feeKrw = feeKrw;
        this.taxKrw = taxKrw;
        this.netAmountKrw = netAmountKrw;
        this.executedAt = executedAt;
    }

    /**
     * 체결 기록 생성. (EXE-004, EXE-007/008)
     * net_amount 계산은 호출 측(OrderService)이 매수/매도에 따라 다르게 계산해서
     * 넘긴다 — 매수: 체결가×수량+수수료, 매도: 체결가×수량-수수료-거래세
     * (정책정의서 1차 7장 net_amount 계산 규칙).
     */
    public static ExecutionEntity create(UUID orderId, long executedPriceKrw, long executedQuantity,
                                         long feeKrw, long taxKrw, long netAmountKrw, Instant executedAt) {
        return new ExecutionEntity(orderId, executedPriceKrw, executedQuantity, feeKrw, taxKrw,
                netAmountKrw, executedAt);
    }
}

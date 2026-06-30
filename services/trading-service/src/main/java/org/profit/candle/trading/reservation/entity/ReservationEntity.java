package org.profit.candle.trading.reservation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "reservation", name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationEntity {

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
    private ReservationSideValue side;

    @Enumerated(EnumType.STRING)
    @Column(name = "timing", nullable = false, length = 20)
    private ReservationTimingValue timing;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_kind", nullable = false, length = 20)
    private ReservationOrderKindValue orderKind;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    /** order_kind == LIMIT일 때만 값 존재 (시가+지정가 케이스). */
    @Column(name = "price_krw")
    private Long priceKrw;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    /** 이 예약이 잠근 금액(수수료 포함). BUY+LIMIT(시가+지정가)만 0보다 큼, 그 외는 0. */
    @Column(name = "reserved_amount_krw", nullable = false)
    private long reservedAmountKrw;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatusValue status;

    /** order 도메인 소유, 값만 복사 — 시가+지정가 전환 완료(EXECUTED) 시에만 채워짐. */
    @Column(name = "converted_order_id")
    private UUID convertedOrderId;

    /** 정정 시 원 예약 참조 (CAN-006/007/008). 동일 reservation 스키마 내부 FK. */
    @Column(name = "parent_reservation_id")
    private UUID parentReservationId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ReservationEntity(UUID id, UUID userId, UUID accountId, String symbol, ReservationSideValue side,
                              ReservationTimingValue timing, ReservationOrderKindValue orderKind, long quantity,
                              Long priceKrw, LocalDate scheduledDate, long reservedAmountKrw,
                              ReservationStatusValue status, UUID parentReservationId, String idempotencyKey,
                              Instant expiresAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.timing = timing;
        this.orderKind = orderKind;
        this.quantity = quantity;
        this.priceKrw = priceKrw;
        this.scheduledDate = scheduledDate;
        this.reservedAmountKrw = reservedAmountKrw;
        this.status = status;
        this.parentReservationId = parentReservationId;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 신규 예약 생성. (RSV-001~008)
     * 가용 가능 금액 검증(BUY)이나 보유 수량 검증(SELL), scheduled_date 범위 검증(RSV-006~008)은
     * 호출 측({@code ReservationService})의 책임이다 — 이 팩토리는 검증된 입력을 받아
     * RESERVED 상태로 생성만 한다.
     *
     * <p>timing별 order_kind 제약(RSV-002/003), order_kind == LIMIT일 때만 price_krw 존재하는
     * 제약은 DB CHECK 제약과 동일하게 여기서도 한 번 더 검사한다 — Entity는 항상 유지되어야
     * 하는 상태 규칙을 스스로 지킨다.</p>
     */
    public static ReservationEntity reserve(UUID userId, UUID accountId, String symbol, ReservationSideValue side,
                                            ReservationTimingValue timing, ReservationOrderKindValue orderKind,
                                            long quantity, Long priceKrw, LocalDate scheduledDate,
                                            long reservedAmountKrw, String idempotencyKey) {
        if (quantity <= 0) {
            throw new ReservationException(ReservationErrorCode.INVALID_QUANTITY);
        }
        validateTimingOrderKind(timing, orderKind);
        validatePrice(orderKind, priceKrw);

        Instant now = Instant.now();
        return new ReservationEntity(UUID.randomUUID(), userId, accountId, symbol, side, timing, orderKind,
                quantity, priceKrw, scheduledDate, reservedAmountKrw, ReservationStatusValue.RESERVED, null,
                idempotencyKey, null, now, now);
    }

    /** RSV-002/003: OPEN은 MARKET/LIMIT만, TODAY_CLOSE/PREV_CLOSE는 AFTER_HOURS_CLOSE만 허용. */
    private static void validateTimingOrderKind(ReservationTimingValue timing, ReservationOrderKindValue orderKind) {
        boolean valid = switch (timing) {
            case OPEN -> orderKind == ReservationOrderKindValue.MARKET
                    || orderKind == ReservationOrderKindValue.LIMIT;
            case TODAY_CLOSE, PREV_CLOSE -> orderKind == ReservationOrderKindValue.AFTER_HOURS_CLOSE;
        };
        if (!valid) {
            throw new ReservationException(ReservationErrorCode.TIMING_ORDER_KIND_MISMATCH);
        }
    }

    /** LIMIT일 때만 price_krw 존재 (시가+지정가 케이스만 가격을 가짐). */
    private static void validatePrice(ReservationOrderKindValue orderKind, Long priceKrw) {
        if (orderKind == ReservationOrderKindValue.LIMIT && priceKrw == null) {
            throw new ReservationException(ReservationErrorCode.LIMIT_RESERVATION_REQUIRES_PRICE);
        }
        if (orderKind != ReservationOrderKindValue.LIMIT && priceKrw != null) {
            throw new ReservationException(ReservationErrorCode.NON_LIMIT_RESERVATION_MUST_NOT_HAVE_PRICE);
        }
        if (priceKrw != null && priceKrw <= 0) {
            throw new ReservationException(ReservationErrorCode.INVALID_PRICE);
        }
    }

    public boolean reserved() {
        return status == ReservationStatusValue.RESERVED;
    }

    /** 시가+지정가 케이스(6종 중 1개)만 이 전이를 탄다 — Order/Reservation 도메인 경계의 핵심. */
    public void startConverting() {
        if (!reserved()) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_NOT_RESERVED);
        }
        if (orderKind != ReservationOrderKindValue.LIMIT || timing != ReservationTimingValue.OPEN) {
            throw new ReservationException(ReservationErrorCode.NOT_CONVERTIBLE);
        }
        this.status = ReservationStatusValue.CONVERTING;
        this.updatedAt = Instant.now();
    }

    /** ReservationConverted 이벤트 수신 시 호출 (order_svc 전환 완료). */
    public void markConverted(UUID convertedOrderId) {
        if (status != ReservationStatusValue.CONVERTING) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_NOT_CONVERTING);
        }
        this.status = ReservationStatusValue.EXECUTED;
        this.convertedOrderId = convertedOrderId;
        this.updatedAt = Instant.now();
    }

    /** 자체 완결 케이스(MARKET/AFTER_HOURS_CLOSE) 배치 체결 처리. */
    public void markExecuted() {
        if (!reserved()) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_NOT_RESERVED);
        }
        this.status = ReservationStatusValue.EXECUTED;
        this.updatedAt = Instant.now();
    }

    /** 취소 처리 (RSV-016/017/018). RESERVED 상태만 취소 가능. */
    public void markCancelled() {
        if (!reserved()) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_NOT_RESERVED);
        }
        this.status = ReservationStatusValue.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /** 배치 처리 실패 (잔고 부족 등). */
    public void markFailed() {
        if (!reserved()) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_NOT_RESERVED);
        }
        this.status = ReservationStatusValue.FAILED;
        this.updatedAt = Instant.now();
    }

    /** 접수 마감 후 미처리 등 자동 만료. */
    public void markExpired() {
        if (!reserved()) {
            throw new ReservationException(ReservationErrorCode.RESERVATION_NOT_RESERVED);
        }
        this.status = ReservationStatusValue.EXPIRED;
        this.updatedAt = Instant.now();
    }

    /** CAN-006/007/008: 정정 = 원예약 취소 + parent 참조를 가진 신규 예약 생성. 신규 측에서 호출. */
    public void linkParent(UUID parentReservationId) {
        this.parentReservationId = parentReservationId;
        this.updatedAt = Instant.now();
    }
}

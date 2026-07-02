package org.profit.candle.trading.reservation.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.reservation.dto.AmendReservationCommand;
import org.profit.candle.trading.reservation.dto.PlaceReservationCommand;
import org.profit.candle.trading.reservation.dto.ReservationCancelResult;
import org.profit.candle.trading.reservation.entity.*;
import org.profit.candle.trading.reservation.event.ReservationCancelledPayload;
import org.profit.candle.trading.reservation.event.ReservationOutboxOperations;
import org.profit.candle.trading.reservation.event.ReservationReservedPayload;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Reservation 도메인 업무 서비스. 메서드는 IdempotencyExecutor의 트랜잭션 안에서 호출되어
 * 상태 변경 + outbox 기록이 멱등성 record와 한 트랜잭션으로 commit된다.
 *
 * 레퍼런스 범위: BUY는 가용 잔고를 예약(reserve)하고 RESERVED 예약 생성, CancelReservation은
 * 예약 해제. AmendReservation은 원예약 취소 + 신규 예약 생성(CAN-006/007/008)으로 처리한다.
 * 배치 실행(scheduled_date 도달 시 시가+지정가 전환/체결)은 도메인 후속 작업(배치 인터페이스)으로
 * 남긴다 — 이 서비스는 사용자 명령(PlaceReservation/CancelReservation/AmendReservation)만 다룬다.
 *
 * <p>DefaultOrderService와 마찬가지로 TradingHoursValidator를 사용하지 않는다 — RSV-003:
 * 예약 주문은 모든 시간대에 상시 접수 가능하므로 거래시간 검증 대상이 아니다. 대신
 * scheduled_date 범위(RSV-006~008)를 자체 검증한다.</p>
 */
@Service
@RequiredArgsConstructor
public class DefaultReservationService implements ReservationService {

    private static final double FEE_RATE = 0.00015;
    private static final int MAX_SCHEDULED_DAYS_AHEAD = 7;

    private final ReservationRepository reservationRepository;
    private final AccountService accountService;
    private final OutboxWriter outboxWriter;
    private final ReservationOutboxOperations outboxOperations;
    private final ReservationDeadlineValidator deadlineValidator;
    private final Clock clock;

    @Override
    @Transactional
    public ReservationEntity placeReservation(UUID userId, PlaceReservationCommand command) {
        if (command.quantity() <= 0) {
            throw new ReservationException(ReservationErrorCode.INVALID_QUANTITY);
        }

        // RSV-006~008: timing별 접수 마감 시간 검증 (KST 기준)
        deadlineValidator.requireBeforeDeadline(command.timing());

        LocalDate scheduledDate = resolveAndValidateScheduledDate(command.timing(), command.scheduledDate());

        // account_id는 reservation이 자체 보유하지 않는 값이라 매 호출 조회한다.
        // (크로스 스키마 FK 금지 — reservations.account_id는 이 시점에 받아온 값을 그대로 저장)
        AccountEntity account = accountService.getAccount(userId);

        // ORD-009 동등 규칙: 동일 종목 RESERVED 예약 중복 방지
        if (reservationRepository.existsByAccountIdAndSymbolAndStatus(
                account.getId(), command.symbol(), ReservationStatusValue.RESERVED)) {
            throw new ReservationException(ReservationErrorCode.DUPLICATE_PENDING_RESERVATION);
        }

        long reservedAmountKrw = 0;
        if (command.side() == ReservationSideValue.BUY) {
            // 시가+지정가 케이스만 price가 존재해 정확한 금액을 미리 계산할 수 있다.
            // 시장가/시간외종가는 체결 시점 가격을 알 수 없으므로, 배치 체결 시점에
            // AccountService.lockBalance를 호출하는 방식은 이 레퍼런스 범위 밖이다 — 일단
            // price가 존재하는 경우(LIMIT)만 선점하고, 그 외(MARKET/AFTER_HOURS_CLOSE)는
            // 0으로 둔다. 배치 체결 로직에서 별도 처리 필요(도메인 후속 작업).
            if (command.price() != null) {
                long amount = command.price() * command.quantity();
                long fee = Math.round(amount * FEE_RATE);
                reservedAmountKrw = amount + fee;
                accountService.lockBalance(userId, reservedAmountKrw);
            }
        }

        ReservationEntity reservation = ReservationEntity.reserve(
                userId, account.getId(), command.symbol(), command.side(), command.timing(), command.kind(),
                command.quantity(), command.price(), scheduledDate, reservedAmountKrw, command.idempotencyKey());
        reservationRepository.save(reservation);

        outboxWriter.record(outboxOperations, "ReservationReserved", reservation.getId().toString(),
                new ReservationReservedPayload(
                        reservation.getId().toString(), userId.toString(), reservation.getSymbol(),
                        reservation.getSide().name(), reservation.getTiming().name(),
                        reservation.getOrderKind().name(), reservation.getQuantity(),
                        reservation.getPriceKrw() == null ? 0 : reservation.getPriceKrw(), reservedAmountKrw));
        return reservation;
    }

    @Override
    @Transactional
    public ReservationCancelResult cancelReservation(UUID userId, UUID reservationId) {
        // 사용자의 취소와 배치 실행이 같은 예약을 동시에 노릴 수 있어
        // 비관적 락으로 조회한다 (findByIdAndUserId가 아니라 ...ForUpdate).
        ReservationEntity reservation = reservationRepository.findByIdAndUserIdForUpdate(reservationId, userId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        return doCancel(reservation, userId);
    }

    @Override
    @Transactional
    public ReservationEntity amendReservation(UUID userId, AmendReservationCommand command) {
        // CAN-006: 배치 마감 전 RESERVED 상태인 예약만 정정 가능.
        ReservationEntity original = reservationRepository.findByIdAndUserIdForUpdate(
                        command.reservationId(), userId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        // CAN-007: 정정은 원예약 취소 + 신규 예약 생성 방식으로 처리한다.
        ReservationCancelResult cancelResult = doCancel(original, userId);

        // null 필드는 원예약 값을 그대로 승계한다 (BFF AmendReservationBody: 모든 필드 선택).
        ReservationTimingValue timing = command.timing() != null ? command.timing() : original.getTiming();
        ReservationOrderKindValue kind = command.kind() != null ? command.kind() : original.getOrderKind();
        long quantity = command.quantity() != null ? command.quantity() : original.getQuantity();
        Long price = command.price() != null ? command.price() : original.getPriceKrw();

        // RSV-006~008: 정정 후 적용될 timing 기준으로 마감 시간 검증.
        // timing을 바꾸는 경우 새 timing, 그대로면 원래 timing 기준으로 검증한다.
        deadlineValidator.requireBeforeDeadline(timing);
        LocalDate scheduledDate = resolveAndValidateScheduledDate(
                timing, command.scheduledDate() != null ? command.scheduledDate() : original.getScheduledDate());

        AccountEntity account = accountService.getAccount(userId);

        long reservedAmountKrw = 0;
        if (original.getSide() == ReservationSideValue.BUY && kind == ReservationOrderKindValue.LIMIT
                && price != null) {
            long amount = price * quantity;
            long fee = Math.round(amount * FEE_RATE);
            reservedAmountKrw = amount + fee;
            accountService.lockBalance(userId, reservedAmountKrw);
        }

        // CAN-008: 정정 이력 연결 — parent_reservation_id로 원래 예약과 연결한다.
        ReservationEntity amended = ReservationEntity.reserve(
                userId, account.getId(), original.getSymbol(), original.getSide(), timing, kind,
                quantity, price, scheduledDate, reservedAmountKrw, command.idempotencyKey());
        amended.linkParent(original.getId());
        reservationRepository.save(amended);

        outboxWriter.record(outboxOperations, "ReservationReserved", amended.getId().toString(),
                new ReservationReservedPayload(
                        amended.getId().toString(), userId.toString(), amended.getSymbol(),
                        amended.getSide().name(), amended.getTiming().name(), amended.getOrderKind().name(),
                        amended.getQuantity(), amended.getPriceKrw() == null ? 0 : amended.getPriceKrw(),
                        reservedAmountKrw));
        return amended;
    }

    private ReservationCancelResult doCancel(ReservationEntity reservation, UUID userId) {
        long releasedAmount = reservation.getReservedAmountKrw();

        // markCancelled()가 RESERVED 여부를 자체 검증한다 (RSV-016/017/018).
        reservation.markCancelled();

        // CAN-004 동등: 취소 시 reserved_amount만큼 즉시 반환. SELL/시장가/시간외종가는 잔고를 잠그지 않으므로 반환 불필요.
        if (releasedAmount > 0 && reservation.getSide() == ReservationSideValue.BUY) {
            accountService.releaseBalance(userId, releasedAmount);
        }

        reservationRepository.save(reservation);

        outboxWriter.record(outboxOperations, "ReservationCancelled", reservation.getId().toString(),
                new ReservationCancelledPayload(reservation.getId().toString(), userId.toString(), releasedAmount));
        return new ReservationCancelResult(reservation, releasedAmount);
    }

    /**
     * RSV-006~008: 전일종가는 항상 내일로 고정, 시가/당일종가는 내일부터 +7일 이내만 허용.
     * scheduledDate가 null이면(시가/당일종가에서 누락) 거부한다 — 전일종가만 자동 고정값을
     * 채워준다.
     */
    private LocalDate resolveAndValidateScheduledDate(ReservationTimingValue timing, LocalDate requested) {
        LocalDate today = LocalDate.now(clock);
        LocalDate tomorrow = today.plusDays(1);

        if (timing == ReservationTimingValue.PREV_CLOSE) {
            return tomorrow;
        }

        if (requested == null) {
            throw new ReservationException(ReservationErrorCode.INVALID_SCHEDULED_DATE);
        }
        LocalDate maxDate = tomorrow.plusDays(MAX_SCHEDULED_DAYS_AHEAD - 1);
        if (requested.isBefore(tomorrow) || requested.isAfter(maxDate)) {
            throw new ReservationException(ReservationErrorCode.INVALID_SCHEDULED_DATE);
        }
        return requested;
    }
}
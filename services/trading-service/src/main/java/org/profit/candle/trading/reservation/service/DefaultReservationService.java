package org.profit.candle.trading.reservation.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.client.MarketSessionClient;
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
import java.time.ZoneId;
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
    private final MarketSessionClient marketSessionClient;
    private final Clock clock;

    @Override
    @Transactional
    public ReservationEntity placeReservation(UUID userId, PlaceReservationCommand command) {
        if (command.quantity() <= 0) {
            throw new ReservationException(ReservationErrorCode.INVALID_QUANTITY);
        }

        LocalDate scheduledDate = resolveAndValidateScheduledDate(command.timing(), command.scheduledDate());

        // RSV-006~008: scheduled_date가 오늘인 예약만 배치 마감 시간 검증 (cancelReservation과 동일 규칙).
        // 예약은 항상 내일 이후로 예정되므로(resolveAndValidateScheduledDate), 미래 예약은 오늘 시각과
        // 무관하게 접수 가능해야 한다 — 과거엔 미래 예약도 오늘 마감시간으로 잘못 거부됐다(예: 15:30 이후 종가예약).
        if (scheduledDate.equals(LocalDate.now(clock))) {
            deadlineValidator.requireBeforeDeadline(command.timing());
        }

        // account_id는 reservation이 자체 보유하지 않는 값이라 매 호출 조회한다.
        // (크로스 스키마 FK 금지 — reservations.account_id는 이 시점에 받아온 값을 그대로 저장)
        AccountEntity account = accountService.getAccount(userId);

        // ORD-009 동등 규칙: 동일 종목·동일 side RESERVED 예약 중복 방지.
        // side를 포함해 반대 side(매수 RESERVED가 있는 상태에서 매도 예약 등)는 서로 막지 않는다.
        // 매도 예약은 잔고를 잠그지 않으므로 반대 side가 공존해도 정합성에 영향 없음.
        if (reservationRepository.existsByAccountIdAndSymbolAndSideAndStatus(
                account.getId(), command.symbol(), command.side(), ReservationStatusValue.RESERVED)) {
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

        // RSV-006~008: scheduled_date가 오늘인 예약만 배치 마감 시간 검증.
        // 미래 날짜 예약은 오늘 배치와 무관하므로 시간 무관하게 취소 가능.
        // KST 기준으로 오늘 날짜를 계산한다 — deadlineValidator도 KST 기준이라 일관성 유지.
        LocalDate todayKst = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        if (reservation.getScheduledDate().equals(todayKst)) {
            deadlineValidator.requireBeforeDeadline(reservation.getTiming());
        }

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

        // 원예약 CANCELLED UPDATE를 신규 RESERVED INSERT보다 먼저 DB에 반영한다.
        // Hibernate 기본 flush 순서(insert→update) 때문에 flush를 강제하지 않으면
        // 신규 INSERT 시점에 원본이 아직 RESERVED로 남아 부분 유니크 인덱스
        // (uq_reservations_account_symbol_reserved: account_id+symbol+side WHERE status='RESERVED')를
        // 위반해 DataIntegrityViolationException → DUPLICATE_PENDING_RESERVATION(422)로 터진다.
        reservationRepository.flush();

        // null 필드는 원예약 값을 그대로 승계한다 (BFF AmendReservationBody: 모든 필드 선택).
        ReservationTimingValue timing = command.timing() != null ? command.timing() : original.getTiming();
        ReservationOrderKindValue kind = command.kind() != null ? command.kind() : original.getOrderKind();
        long quantity = command.quantity() != null ? command.quantity() : original.getQuantity();
        Long price = command.price() != null ? command.price() : original.getPriceKrw();

        LocalDate scheduledDate = resolveAndValidateScheduledDate(
                timing, command.scheduledDate() != null ? command.scheduledDate() : original.getScheduledDate());
        // RSV-006~008: scheduled_date가 오늘인 예약만 마감 시간 검증 (place/cancelReservation과 동일 규칙).
        // 정정 후 적용될 timing 기준으로 검증한다(timing 변경 시 새 timing).
        if (scheduledDate.equals(LocalDate.now(clock))) {
            deadlineValidator.requireBeforeDeadline(timing);
        }

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
        // 원예약을 이미 취소했고(위 doCancel) 동일 side로 재생성하는 것이라 중복 검증을
        // 다시 태우지 않는다 — placeReservation과 달리 amend는 existsBy 체크를 하지 않는다(기존 동작 유지).
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

        LocalDate resolved;
        if (timing == ReservationTimingValue.PREV_CLOSE) {
            resolved = tomorrow;
        } else {
            if (requested == null) {
                throw new ReservationException(ReservationErrorCode.INVALID_SCHEDULED_DATE);
            }
            LocalDate maxDate = tomorrow.plusDays(MAX_SCHEDULED_DAYS_AHEAD - 1);
            if (requested.isBefore(tomorrow) || requested.isAfter(maxDate)) {
                throw new ReservationException(ReservationErrorCode.INVALID_SCHEDULED_DATE);
            }
            resolved = requested;
        }

        // RSV: 실행 예정일이 거래일이어야 한다 — 주말·휴장일 예약을 막는다.
        // 판정은 권위 소스(market-service MarketSession, 공휴일 캘린더 포함)에 위임한다.
        if (!marketSessionClient.isTradingDay(resolved)) {
            throw new ReservationException(ReservationErrorCode.SCHEDULED_DATE_NOT_TRADING_DAY);
        }
        return resolved;
    }
}
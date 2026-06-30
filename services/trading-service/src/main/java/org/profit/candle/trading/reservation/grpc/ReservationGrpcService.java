package org.profit.candle.trading.reservation.grpc;


import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.reservation.dto.PlaceReservationCommand;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.entity.ReservationSideValue;
import org.profit.candle.trading.reservation.entity.ReservationStatusValue;
import org.profit.candle.trading.reservation.event.ReservationOutboxOperations;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.reservation.service.ReservationService;
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
public class ReservationGrpcService implements ReservationService {

    private static final double FEE_RATE = 0.00015;
    private static final int MAX_SCHEDULED_DAYS_AHEAD = 7;

    private final ReservationRepository reservationRepository;
    private final AccountService accountService;
    private final OutboxWriter outboxWriter;
    private final ReservationOutboxOperations outboxOperations;
    private final Clock clock;

    @Override
    @Transactional
    public ReservationEntity placeReservation(UUID userId, PlaceReservationCommand command) {
        if (command.quantity() <= 0) {
            throw new ReservationException(ReservationErrorCode.INVALID_QUANTITY);
        }

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
        if (command.side() == ReservationSideValue.BUY)
    }
}

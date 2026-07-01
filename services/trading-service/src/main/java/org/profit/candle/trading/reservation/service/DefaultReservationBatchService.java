package org.profit.candle.trading.reservation.service;

import lombok.RequiredArgsConstructor;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationStatusValue;
import org.profit.candle.trading.reservation.entity.ReservationTimingValue;
import org.profit.candle.trading.reservation.event.ReservationDuePayload;
import org.profit.candle.trading.reservation.event.ReservationOutboxOperations;
import org.profit.candle.trading.reservation.exception.ReservationErrorCode;
import org.profit.candle.trading.reservation.exception.ReservationException;
import org.profit.candle.trading.reservation.repository.ReservationRepository;
import org.profit.candle.trading.support.event.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 배치 체결 처리 구현체. DefaultReservationService(사용자 명령)와 분리해
 * 배치 전용 트랜잭션 경계와 로직을 독립적으로 관리한다.
 *
 * <p>현재 구현 범위: OPEN+LIMIT(시가+지정가)만 처리한다.
 * OPEN+MARKET / TODAY_CLOSE / PREV_CLOSE는 Market 도메인 Kafka 스키마 확정 후 추가 예정.</p>
 */

@Service
@RequiredArgsConstructor
public class DefaultReservationBatchService implements ReservationBatchService{

    private final ReservationRepository reservationRepository;
    private final OutboxWriter outboxWriter;
    private final ReservationOutboxOperations outboxOperations;

    @Override
    @Transactional
    public int processOpenLimitReservations(LocalDate targetDate) {
        List<ReservationEntity> targets = reservationRepository
                .findByScheduledDateAndStatusAndTiming(
                        targetDate, ReservationStatusValue.RESERVED, ReservationTimingValue.OPEN);

        int count = 0;
        for (ReservationEntity reservation : targets) {
            // OPEN 타이밍 중 LIMIT 케이스만 처리 — MARKET은 별도 구현 예정
            if (reservation.getOrderKind() != ReservationOrderKindValue.LIMIT) continue;

            // CONVERTING 전이 + Outbox 기록은 한 트랜잭션 안에서 처리된다 (컨벤션 7장).
            reservation.startConverting();
            reservationRepository.save(reservation);

            outboxWriter.record(outboxOperations, "ReservationDue", reservation.getId().toString(),
                    new ReservationDuePayload(
                            reservation.getId().toString(),
                            reservation.getUserId().toString(),
                            reservation.getAccountId().toString(),
                            reservation.getSymbol(),
                            reservation.getSide().name(),
                            reservation.getQuantity(),
                            reservation.getPriceKrw(),
                            reservation.getIdempotencyKey()));

            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public void markConverted(UUID reservationId, UUID convertedOrderId) {
        // 배치 시스템이 호출 — userId 소유권 검증 없이 시스템 권한으로 처리한다.
        // order의 cancelExpiredPendingOrder(findByIdForUpdate)와 동일한 패턴.
        ReservationEntity reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        reservation.markConverted(convertedOrderId);
        reservationRepository.save(reservation);
    }
}

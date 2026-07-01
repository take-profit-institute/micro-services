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
public class DefaultReservationBatchService implements ReservationBatchService {

    private final ReservationRepository reservationRepository;
    private final OutboxWriter outboxWriter;
    private final ReservationOutboxOperations outboxOperations;

    @Override
    @Transactional
    public int processOpenLimitReservations(LocalDate targetDate) {
        // ── 트랜잭션 설계 결정 ────────────────────────────────────────────
        // 현재: @Transactional 하나로 당일 OPEN+LIMIT 전체를 묶음.
        // 한 건이라도 예외가 나면 전체 롤백되는 리스크가 있다.
        //
        // 이 구조를 선택한 이유:
        //   - startConverting()이 실패하는 케이스는 !reservation.reserved()뿐이고,
        //     루프 진입 전 이미 continue로 걸러냄.
        //   - save()/outboxWriter.record() 실패 시 해당 건은 RESERVED 상태로 남아
        //     다음 배치 실행에서 재처리된다 — 실질적 전체 롤백 리스크가 낮음.
        //   - 7/9 발표 일정상 단순 구조를 우선함.
        //
        // 개선 방안 (발표 후 리팩토링 이슈로 고려):
        //
        // [방안 A] 배치 담당자가 건별로 gRPC 호출하도록 proto 변경 (권장)
        //   - ProcessOpenLimitReservationsRequest의 scheduled_date 대신 reservation_id를 받음.
        //   - 배치 서비스가 대상 목록을 조회한 뒤 reservation_id마다 RPC를 따로 호출.
        //   - order의 ExpirePendingOrders/cancelExpiredPendingOrder와 완전히 동일한 패턴.
        //   - trading-service 코드 변경 없이 proto + 배치 서비스만 수정하면 됨.
        //   - 단점: 배치 담당자 협의 필요, RPC 호출 횟수 증가(건수만큼).
        //
        // [방안 B] 건별 트랜잭션을 위한 Helper 클래스 분리
        //   - DefaultReservationBatchServiceHelper를 별도 @Service로 만들고,
        //     processSingleOpenLimitReservation(UUID reservationId) @Transactional 메서드를 둠.
        //   - DefaultReservationBatchService는 candidates 조회 후 Helper를 건별로 호출.
        //   - Spring AOP 프록시를 통해 호출하므로 @Transactional이 정상 동작.
        //   - proto/배치 담당자 협의 없이 내부 코드만으로 해결 가능.
        //   - 단점: 클래스 1개 추가, 스프링 빈 추가.
        // ─────────────────────────────────────────────────────────────────
        List<ReservationEntity> candidates = reservationRepository
                .findByScheduledDateAndStatusAndTiming(
                        targetDate, ReservationStatusValue.RESERVED, ReservationTimingValue.OPEN);

        int count = 0;
        for (ReservationEntity candidate : candidates) {
            if (candidate.getOrderKind() != ReservationOrderKindValue.LIMIT) continue;

            // 개별 락 획득 — 이미 다른 트랜잭션이 상태를 바꿨으면 RESERVED가 아닐 수 있다.
            ReservationEntity reservation = reservationRepository
                    .findByIdForUpdate(candidate.getId())
                    .orElse(null);
            if (reservation == null || !reservation.reserved()) continue;

            // CONVERTING 전이 + Outbox 기록은 한 트랜잭션 안에서 처리된다 (컨벤션 7장).
            reservation.startConverting();
            reservationRepository.save(reservation);

            outboxWriter.record(outboxOperations, "ReservationDue",
                    reservation.getId().toString(),
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

        // 멱등 처리: 이미 EXECUTED면 성공으로 간주 — at-least-once 재시도 대응.
        // order_svc가 MarkReservationConverted를 재시도할 경우 RESERVATION_NOT_CONVERTING으로
        // 실패하는 대신 조용히 통과한다.
        if (reservation.getStatus() == ReservationStatusValue.EXECUTED) {
            return;
        }

        reservation.markConverted(convertedOrderId);
        reservationRepository.save(reservation);
    }
}
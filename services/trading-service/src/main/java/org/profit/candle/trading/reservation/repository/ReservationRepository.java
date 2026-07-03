package org.profit.candle.trading.reservation.repository;

import jakarta.persistence.LockModeType;
import org.profit.candle.trading.reservation.entity.ReservationEntity;
import org.profit.candle.trading.reservation.entity.ReservationOrderKindValue;
import org.profit.candle.trading.reservation.entity.ReservationStatusValue;
import org.profit.candle.trading.reservation.entity.ReservationTimingValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {
    /** RSV-009 상세: 본인 예약 상세 조회. userId까지 같이 받아 본인 소유가 아니면 빈 결과로 권한을 체크한다. */
    Optional<ReservationEntity> findByIdAndUserId(UUID id, UUID userId);

    /** RSV-009: 본인 예약 목록 조회. BFF 명세상 페이징 파라미터가 없어(status 필터만 존재) 전체 목록을
     *  반환한다 — order의 findByUserIdOrderByCreatedAtDesc와 동일한 패턴. */
    List<ReservationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** RSV-009: 상태 필터가 적용된 본인 예약 목록 조회. */
    List<ReservationEntity> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, ReservationStatusValue status);

    /** ORD-009 동등 규칙: 동일 계좌·동일 종목 RESERVED 예약 존재 여부. */
    boolean existsByAccountIdAndSymbolAndStatus(UUID accountId, String symbol, ReservationStatusValue status);

    /**
     * 취소/정정 처리 직전 락을 걸고 조회한다. 사용자의 CancelReservation/AmendReservation과
     * 배치 실행(scheduled_date 도달 시 처리)이 같은 예약을 동시에 다루는 레이스 컨디션을 막는다 —
     * order의 findByIdAndUserIdForUpdate와 동일한 목적이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.id = :id and r.userId = :userId")
    Optional<ReservationEntity> findByIdAndUserIdForUpdate(UUID id, UUID userId);

    /**
     * 배치 처리 대상 조회용. userId 제약 없이 시스템이 전체를 훑는다.
     * idx_reservations_batch_lookup(scheduled_date, status, timing WHERE status = 'RESERVED')을 탄다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.id = :id")
    Optional<ReservationEntity> findByIdForUpdate(UUID id);

    /**
     * 오늘 처리할 RESERVED 건 조회 (배치 진입점). scheduled_date + status + timing 조합으로
     * idx_reservations_batch_lookup을 탄다.
     */
    List<ReservationEntity> findByScheduledDateAndStatusAndTiming(
            LocalDate scheduledDate, ReservationStatusValue status, ReservationTimingValue timing);

    /**
     * EXPIRED 처리 대상 조회 — timing 무관, scheduled_date + status 조합.
     * 15:40 이후 당일 아직 RESERVED인 예약 전체를 조회한다.
     */
    List<ReservationEntity> findByScheduledDateAndStatus(
            LocalDate scheduledDate, ReservationStatusValue status);

    /**
     * OPEN+MARKET 배치 체결 대상 조회. scheduled_date + status + timing + orderKind + symbol
     * 조합으로 DB에서 직접 필터링한다 — Java stream 필터링 대비 DB 부하 감소.
     */
    @Query("""
            select r from ReservationEntity r
            where r.scheduledDate = :scheduledDate
              and r.status = :status
              and r.timing = :timing
              and r.orderKind = :orderKind
              and r.symbol = :symbol
            """)
    List<ReservationEntity> findByScheduledDateAndStatusAndTimingAndOrderKindAndSymbol(
            @Param("scheduledDate") LocalDate scheduledDate,
            @Param("status") ReservationStatusValue status,
            @Param("timing") ReservationTimingValue timing,
            @Param("orderKind") ReservationOrderKindValue orderKind,
            @Param("symbol") String symbol);
}
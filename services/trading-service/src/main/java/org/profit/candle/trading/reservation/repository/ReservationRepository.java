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

    /** RSV-009 상세: 본인 예약 상세 조회. */
    Optional<ReservationEntity> findByIdAndUserId(UUID id, UUID userId);

    /** RSV-009: 본인 예약 목록 조회. */
    List<ReservationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** RSV-009: 상태 필터가 적용된 본인 예약 목록 조회. */
    List<ReservationEntity> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, ReservationStatusValue status);

    /** ORD-009 동등 규칙: 동일 계좌·동일 종목 RESERVED 예약 존재 여부. */
    boolean existsByAccountIdAndSymbolAndStatus(UUID accountId, String symbol, ReservationStatusValue status);

    /**
     * 취소/정정 처리 직전 락을 걸고 조회한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.id = :id and r.userId = :userId")
    Optional<ReservationEntity> findByIdAndUserIdForUpdate(UUID id, UUID userId);

    /**
     * 배치 처리 대상 조회용. userId 제약 없이 시스템이 전체를 훑는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.id = :id")
    Optional<ReservationEntity> findByIdForUpdate(UUID id);

    /**
     * 오늘 처리할 RESERVED 건 조회 (배치 진입점).
     */
    List<ReservationEntity> findByScheduledDateAndStatusAndTiming(
            LocalDate scheduledDate, ReservationStatusValue status, ReservationTimingValue timing);

    /**
     * EXPIRED 처리 대상 조회 — timing 무관, scheduled_date + status 조합.
     */
    List<ReservationEntity> findByScheduledDateAndStatus(
            LocalDate scheduledDate, ReservationStatusValue status);

    /**
     * CONVERTING 타임아웃 처리용 id 목록 조회.
     * 당일 scheduled_date이고 아직 CONVERTING 상태인 예약 id만 조회한다.
     */
    @Query("select r.id from ReservationEntity r " +
            "where r.scheduledDate = :scheduledDate " +
            "and r.status = org.profit.candle.trading.reservation.entity.ReservationStatusValue.CONVERTING")
    List<UUID> findStaleConvertingReservationIds(
            @Param("scheduledDate") LocalDate scheduledDate);

    /**
     * 건별 OPEN+LIMIT 배치 처리용 id 목록 조회.
     * 엔티티 전체 로딩 없이 id만 조회한다.
     */
    @Query("select r.id from ReservationEntity r " +
            "where r.scheduledDate = :scheduledDate " +
            "and r.status = :status " +
            "and r.timing = org.profit.candle.trading.reservation.entity.ReservationTimingValue.OPEN " +
            "and r.orderKind = org.profit.candle.trading.reservation.entity.ReservationOrderKindValue.LIMIT")
    List<UUID> findOpenLimitReservationIds(
            @Param("scheduledDate") LocalDate scheduledDate,
            @Param("status") ReservationStatusValue status);

    /**
     * EXPIRED 처리용 id 목록 조회 — timing 무관, id만 조회.
     */
    @Query("select r.id from ReservationEntity r " +
            "where r.scheduledDate = :scheduledDate " +
            "and r.status = :status")
    List<UUID> findExpirableReservationIds(
            @Param("scheduledDate") LocalDate scheduledDate,
            @Param("status") ReservationStatusValue status);

    /**
     * OPEN+MARKET 배치 체결 대상 조회.
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
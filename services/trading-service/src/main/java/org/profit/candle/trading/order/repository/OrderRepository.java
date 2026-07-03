package org.profit.candle.trading.order.repository;

import jakarta.persistence.LockModeType;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.profit.candle.trading.order.service.OrderLimitFillExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    /** ORD-005: 본인 주문 상세 조회. userId까지 같이 받아 본인 소유가 아니면 빈 결과로 권한을 체크한다. */
    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);

    /** ORD-004: 본인 주문 목록 조회. */
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** ORD-004: 상태 필터가 적용된 본인 주문 목록 조회. */
    List<OrderEntity> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, OrderStatusValue status);

    /** ORD-009: 동일 계좌·동일 종목 PENDING 주문 존재 여부. DB의 부분 unique index가 최종 방어선이다. */
    boolean existsByAccountIdAndSymbolAndStatus(UUID accountId, String symbol, OrderStatusValue status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id and o.userId = :userId")
    Optional<OrderEntity> findByIdAndUserIdForUpdate(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(UUID id);

    @Query("select o.id from OrderEntity o where o.status = :status and o.orderKind = "
            + "org.profit.candle.trading.order.entity.OrderKindValue.LIMIT")
    List<UUID> findIdsByStatus(OrderStatusValue status);

    /**
     * EXE-002: 지정가 조건 체결 후보 조회.
     * symbol + PENDING + LIMIT 조합으로 현재가 이벤트 수신 시 체결 가능 여부를 검사할 주문을 가져온다.
     * 락 없이 id만 가볍게 가져온다 — 실제 체결은 {@link OrderLimitFillExecutor}가
     * 건별로 findByIdForUpdate로 락을 잡고 처리한다.
     */
    @Query("select o from OrderEntity o where o.symbol = :symbol and o.status = :status " +
            "and o.orderKind = org.profit.candle.trading.order.entity.OrderKindValue.LIMIT")
    List<OrderEntity> findPendingLimitOrdersBySymbol(
            @Param("symbol") String symbol,
            @Param("status") OrderStatusValue status);
}
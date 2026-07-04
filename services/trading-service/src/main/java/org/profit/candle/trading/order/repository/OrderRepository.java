package org.profit.candle.trading.order.repository;

import jakarta.persistence.LockModeType;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderKindValue;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    /**
     * ORD-005: 본인 주문 상세 조회. userId까지 같이 받아 본인 소유가 아니면 빈 결과로 권한을 체크한다.
     */
    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);

    /**
     * ORD-004: 본인 주문 목록 조회.
     */
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * ORD-004: 상태 필터가 적용된 본인 주문 목록 조회.
     */
    List<OrderEntity> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, OrderStatusValue status);

    /**
     * ORD-009: 동일 계좌·동일 종목 PENDING 주문 존재 여부. DB의 부분 unique index가 최종 방어선이다.
     */
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
     * symbol + PENDING + LIMIT 조합으로 현재가 이벤트 수신 시 체결 가능 여부를 검사할 주문을 조회한다.
     * 1차 필터(side/priceKrw)에 필요한 필드만 Projection으로 조회해 엔티티 전체 로딩을 피한다.
     * 실제 체결은 서비스 레이어가 건별로 findByIdForUpdate로 락을 잡고 처리한다.
     */
    @Query("select o.id as id, o.side as side, o.priceKrw as priceKrw " +
            "from OrderEntity o where o.symbol = :symbol and o.status = :status " +
            "and o.orderKind = org.profit.candle.trading.order.entity.OrderKindValue.LIMIT")
    List<LimitOrderCandidate> findPendingLimitOrdersBySymbol(
            @Param("symbol") String symbol,
            @Param("status") OrderStatusValue status);

    /**
     * EXE-002 후보 조회용 Projection — id/side/priceKrw만 로드한다.
     */
    interface LimitOrderCandidate {
        UUID getId();

        org.profit.candle.trading.order.entity.OrderSideValue getSide();

        Long getPriceKrw();
    }

    /**
     * ReservationDue 멱등 처리 — 동일 idempotencyKey로 이미 생성된 Order 조회.
     * Kafka 재시도 시 이미 생성된 Order를 그대로 반환해 중복 생성을 방지한다.
     */
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
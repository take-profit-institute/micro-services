package org.profit.candle.trading.order.repository;

import jakarta.persistence.LockModeType;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * 취소 처리 직전 락을 걸고 조회한다. 사용자의 CancelOrder와 배치의 15:30
     * 자동취소(일반 지정가 미체결 전체 대상)가 같은 주문을 동시에 취소 처리하는
     * 레이스 컨디션을 막는다 — 둘 중 하나가 먼저 락을 잡고 markCancelled() +
     * releaseBalance까지 끝내면, 뒤따라온 트랜잭션은 이미 CANCELLED로 바뀐
     * 상태를 보고 ORDER_NOT_PENDING으로 자연스럽게 막힌다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id and o.userId = :userId")
    Optional<OrderEntity> findByIdAndUserIdForUpdate(UUID id, UUID userId);

    /**
     * 배치 자동취소(RSV-014 일반화 — 모든 즉시 지정가 PENDING 주문, 15:30 마감)
     * 대상 조회용. userId 제약 없이 시스템이 전체를 훑는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(UUID id);
}

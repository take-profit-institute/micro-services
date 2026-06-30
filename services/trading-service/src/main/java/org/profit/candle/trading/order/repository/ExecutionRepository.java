package org.profit.candle.trading.order.repository;

import org.profit.candle.trading.order.entity.ExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, Long> {

    /** EXE-005: 본인 체결 내역 조회용 — order_id로 단건 조회. 1주문=1체결(UNIQUE)이라 단건. */
    java.util.Optional<ExecutionEntity> findByOrderId(UUID orderId);

    /** 여러 주문의 체결 내역 일괄 조회 (목록 화면용). */
    List<ExecutionEntity> findByOrderIdIn(List<UUID> orderIds);
}

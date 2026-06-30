package org.profit.candle.trading.order.repository;

import org.profit.candle.trading.order.entity.OrderEntity;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<OrderEntity> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, OrderStatusValue status);
    boolean existsByAccountIdAndSymbolAndStatus(UUID accountId, String symbol, OrderStatusValue status);
}

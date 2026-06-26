package org.profit.candle.trading.order.repository;

import java.util.List;
import java.util.Optional;
import org.profit.candle.trading.order.entity.OrderStatusValue;
import org.profit.candle.trading.order.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    Optional<OrderEntity> findByIdAndUserId(String id, String userId);
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    List<OrderEntity> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, OrderStatusValue status);
    boolean existsByUserIdAndSymbolAndStatus(String userId, String symbol, OrderStatusValue status);
}

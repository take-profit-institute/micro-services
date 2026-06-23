package org.profit.candle.user.profile.event.repository;

import java.util.UUID;
import org.profit.candle.user.profile.event.entity.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {}

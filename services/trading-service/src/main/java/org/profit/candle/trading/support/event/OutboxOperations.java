package org.profit.candle.trading.support.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 도메인별 outbox Repository를 {@link OutboxWriter}가 다룰 수 있는 최소 형태로
 * 감싸는 인터페이스. 호출하는 쪽(AccountService 등)이 자기 도메인의 Repository를
 * 이 인터페이스로 어댑팅해 넘긴다.
 *
 * @param <REC> 도메인의 outbox event 엔티티 타입
 */
public interface OutboxOperations<REC> {

    REC newEvent(UUID id, String eventType, String aggregateId, String payload, Instant occurredAt);

    void save(REC event);
}
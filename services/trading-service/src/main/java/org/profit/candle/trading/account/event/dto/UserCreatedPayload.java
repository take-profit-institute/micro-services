package org.profit.candle.trading.account.event.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * auth.user-created.v1 토픽 페이로드.
 * user 서비스의 UserCreatedPayload와 동일 스키마 — account 도메인이 직접 정의한다
 * (별도 배포 단위라 user 서비스 Java 코드를 직접 참조하지 않는다, 컨벤션 1장).
 * account 도메인은 email 필드를 사용하지 않지만, 역직렬화 실패 방지를 위해
 * 페이로드 스키마 전체를 그대로 선언한다.
 */
public record UserCreatedPayload(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID userId,
        String email,
        Instant occurredAt) {}
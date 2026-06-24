package org.profit.candle.trading.support.idempotency;

import java.time.Instant;
import java.util.Optional;

/**
 * 도메인별 idempotency Repository를 {@link IdempotencyExecutor}가 다룰 수 있는
 * 최소 형태로 감싸는 인터페이스. 호출하는 쪽(AccountGrpcService 등)이 자기 도메인의
 * Repository를 이 인터페이스로 어댑팅해 넘긴다.
 *
 * @param <ID>  도메인의 idempotency record 복합키 타입
 * @param <REC> 도메인의 idempotency record 엔티티 타입
 */
public interface IdempotencyOperations<ID, REC> {

    ID newId(String actorId, String operation, String idempotencyKey);

    /** id로 record를 조회한다. 결과가 없을 수 있으므로 Optional을 반환한다. */
    Optional<REC> findById(ID id);

    void save(REC record);

    REC newRecord(ID id, String requestHash, byte[] responsePayload,
                  String responseType, String grpcCode, Instant expiresAt);

    String requestHash(REC record);

    byte[] responsePayload(REC record);
}
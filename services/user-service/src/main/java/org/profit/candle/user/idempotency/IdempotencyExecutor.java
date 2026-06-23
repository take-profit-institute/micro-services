package org.profit.candle.user.idempotency;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import io.grpc.Status;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.profit.candle.user.idempotency.entity.IdempotencyRecord;
import org.profit.candle.user.idempotency.entity.IdempotencyRecordId;
import org.profit.candle.user.idempotency.repository.IdempotencyRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 쓰기 gRPC 명령을 멱등하게 실행한다.
 *
 * 1. canonical request hash 계산
 * 2. record 조회: same key+diff hash → ALREADY_EXISTS / same key+same hash → 저장 response 재생
 * 3. 없음 → 트랜잭션: 도메인 변경 + record insert를 한 번에 commit
 * 4. unique 충돌 → 재조회 후 재생 (동시 요청을 primary key가 직렬화)
 */
@Component
public class IdempotencyExecutor {

    private final IdempotencyRecordRepository repository;
    private final RequestHasher hasher;
    private final TransactionTemplate transactionTemplate;
    private final Duration defaultTtl;

    public IdempotencyExecutor(IdempotencyRecordRepository repository,
                               RequestHasher hasher,
                               PlatformTransactionManager transactionManager,
                               @Value("${user.idempotency.default-ttl:P1D}") Duration defaultTtl) {
        this.repository = repository;
        this.hasher = hasher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.defaultTtl = defaultTtl;
    }

    public <R extends Message> R execute(Message request, Parser<R> responseParser, Supplier<R> command) {
        IdempotencyContext context = IdempotencyContext.current();
        if (context == null || isBlank(context.idempotencyKey())) {
            throw Status.INVALID_ARGUMENT.withDescription("IDEMPOTENCY_KEY_INVALID").asRuntimeException();
        }
        if (isBlank(context.actorId())) {
            throw Status.UNAUTHENTICATED.withDescription("MISSING_ACTOR").asRuntimeException();
        }

        String requestHash = hasher.hash(context.operation(), context.actorId(), request);
        IdempotencyRecordId id = new IdempotencyRecordId(
                context.actorId(), context.operation(), context.idempotencyKey());

        IdempotencyRecord existing = repository.findById(id).orElse(null);
        if (existing != null) {
            return replay(existing, requestHash, responseParser);
        }

        try {
            return transactionTemplate.execute(status -> {
                R response = command.get();
                repository.saveAndFlush(new IdempotencyRecord(
                        id,
                        requestHash,
                        response.toByteArray(),
                        response.getDescriptorForType().getFullName(),
                        Status.Code.OK.name(),
                        Instant.now().plus(defaultTtl)));
                return response;
            });
        } catch (DataIntegrityViolationException race) {
            IdempotencyRecord winner = repository.findById(id).orElseThrow(() -> race);
            return replay(winner, requestHash, responseParser);
        }
    }

    private <R extends Message> R replay(IdempotencyRecord record, String requestHash, Parser<R> parser) {
        if (!record.requestHash().equals(requestHash)) {
            throw Status.ALREADY_EXISTS
                    .withDescription("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST")
                    .asRuntimeException();
        }
        try {
            return parser.parseFrom(record.responsePayload());
        } catch (InvalidProtocolBufferException e) {
            throw Status.INTERNAL
                    .withDescription("idempotency replay decode failed")
                    .withCause(e)
                    .asRuntimeException();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

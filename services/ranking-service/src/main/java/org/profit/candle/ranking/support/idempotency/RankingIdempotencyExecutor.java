package org.profit.candle.ranking.support.idempotency;

import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingRequest;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RankingIdempotencyExecutor {

    private static final Duration RECORD_TTL = Duration.ofHours(24);
    private static final String EVENT_TYPE = "DailyRankingFinalized";

    private final RankingCommandRepository repository;
    private final RequestHasher requestHasher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public RankingIdempotencyExecutor(
            RankingCommandRepository repository,
            RequestHasher requestHasher,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.repository = repository;
        this.requestHasher = requestHasher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** FinalizeDailyRanking을 한 번만 실행하고 같은 요청에는 저장된 응답을 재생한다. */
    public FinalizeDailyRankingResponse execute(
            FinalizeDailyRankingRequest request,
            Supplier<FinalizeDailyRankingResponse> command) {
        IdempotencyContext context = requireContext(request);
        repository.deleteExpiredRecords(clock.instant());
        String requestHash = requestHasher.hash(context.operation(), context.actorId(), request);
        var existing = repository.findRecord(
                context.actorId(), context.operation(), context.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        try {
            return transactionTemplate.execute(status -> {
                FinalizeDailyRankingResponse response = command.get();
                Instant now = clock.instant();
                repository.saveOutbox(new RankingCommandRepository.OutboxEvent(
                        eventId(context),
                        EVENT_TYPE,
                        response.getRankingDate(),
                        payload(response),
                        now));
                repository.saveRecord(new RankingCommandRepository.IdempotencyRecord(
                        context.actorId(),
                        context.operation(),
                        context.idempotencyKey(),
                        requestHash,
                        response.toByteArray(),
                        response.getDescriptorForType().getFullName(),
                        now.plus(RECORD_TTL)));
                return response;
            });
        } catch (DataIntegrityViolationException race) {
            var winner = repository.findRecord(
                            context.actorId(), context.operation(), context.idempotencyKey())
                    .orElseThrow(() -> race);
            return replay(winner, requestHash);
        }
    }

    /** 한 시간마다 TTL이 지난 성공 응답을 삭제한다. */
    @Scheduled(fixedDelay = 3_600_000L)
    public void cleanExpiredRecords() {
        repository.deleteExpiredRecords(clock.instant());
    }

    /** metadata와 request의 key 및 인증 주체를 검증한다. */
    private IdempotencyContext requireContext(FinalizeDailyRankingRequest request) {
        IdempotencyContext context = IdempotencyContext.current();
        if (context == null || blank(context.idempotencyKey())
                || !request.hasCommandMetadata()
                || !context.idempotencyKey().equals(request.getCommandMetadata().getIdempotencyKey())) {
            throw Status.INVALID_ARGUMENT.withDescription("IDEMPOTENCY_KEY_INVALID").asRuntimeException();
        }
        if (blank(context.actorId())) {
            throw Status.UNAUTHENTICATED.withDescription("MISSING_ACTOR").asRuntimeException();
        }
        return context;
    }

    /** 같은 hash면 protobuf 응답을 복원하고 다른 hash면 key 재사용을 거절한다. */
    private FinalizeDailyRankingResponse replay(
            RankingCommandRepository.IdempotencyRecord record,
            String requestHash) {
        if (!record.requestHash().equals(requestHash)) {
            throw Status.ALREADY_EXISTS
                    .withDescription("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST")
                    .asRuntimeException();
        }
        try {
            return FinalizeDailyRankingResponse.parseFrom(record.responsePayload());
        } catch (Exception exception) {
            throw Status.INTERNAL.withDescription("IDEMPOTENCY_RESPONSE_INVALID")
                    .withCause(exception).asRuntimeException();
        }
    }

    /** Outbox에 저장할 안정적인 JSON payload를 만든다. */
    private String payload(FinalizeDailyRankingResponse response) {
        return "{\"rankingDate\":\"" + response.getRankingDate()
                + "\",\"rankedUserCount\":" + response.getRankedUserCount() + "}";
    }

    /** 같은 command 재시도에서 동일한 Outbox event ID가 되도록 결정적으로 생성한다. */
    private UUID eventId(IdempotencyContext context) {
        String scope = context.actorId() + "\n" + context.operation() + "\n" + context.idempotencyKey();
        return UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8));
    }

    /** 문자열이 비어 있는지 검사한다. */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

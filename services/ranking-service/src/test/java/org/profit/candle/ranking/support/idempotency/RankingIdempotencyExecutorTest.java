package org.profit.candle.ranking.support.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.proto.common.v1.CommandMetadata;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingRequest;
import org.profit.candle.proto.ranking.v1.FinalizeDailyRankingResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionDefinition;

@ExtendWith(MockitoExtension.class)
class RankingIdempotencyExecutorTest {

    private static final String ACTOR = "batch-service";
    private static final String OPERATION = "candle.ranking.v1.RankingService/FinalizeDailyRanking";
    private static final String KEY = "11111111-1111-4111-8111-111111111111";

    @Mock
    RankingCommandRepository repository;

    @Mock
    PlatformTransactionManager transactionManager;

    @Mock
    TransactionStatus transactionStatus;

    private RequestHasher requestHasher;
    private RankingIdempotencyExecutor executor;

    /** 고정 시각과 가짜 transaction manager로 executor를 준비한다. */
    @BeforeEach
    void setUp() {
        requestHasher = new RequestHasher();
        org.mockito.Mockito.lenient()
                .when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        executor = new RankingIdempotencyExecutor(
                repository,
                requestHasher,
                transactionManager,
                Clock.fixed(Instant.parse("2026-07-03T06:30:00Z"), ZoneOffset.UTC));
    }

    /** 최초 요청이 명령·Outbox·성공 응답을 각각 한 번 저장하는지 검증한다. */
    @Test
    void executeStoresOutboxAndResponseOnce() throws Exception {
        FinalizeDailyRankingRequest request = request("2026-07-03", KEY);
        FinalizeDailyRankingResponse response = response("2026-07-03", 2);
        when(repository.findRecord(ACTOR, OPERATION, KEY)).thenReturn(Optional.empty());

        FinalizeDailyRankingResponse actual = inContext(
                KEY, () -> executor.execute(request, () -> response));

        assertThat(actual).isEqualTo(response);
        verify(repository).saveOutbox(any());
        verify(repository).saveRecord(any());
        verify(transactionManager).commit(transactionStatus);
    }

    /** 같은 key와 같은 요청은 명령을 다시 실행하지 않고 저장 응답을 재생하는지 검증한다. */
    @Test
    void executeReplaysTheSavedResponse() throws Exception {
        FinalizeDailyRankingRequest request = request("2026-07-03", KEY);
        FinalizeDailyRankingResponse response = response("2026-07-03", 2);
        when(repository.findRecord(ACTOR, OPERATION, KEY)).thenReturn(Optional.of(record(request, response)));
        AtomicInteger executions = new AtomicInteger();

        FinalizeDailyRankingResponse actual = inContext(KEY, () -> executor.execute(request, () -> {
            executions.incrementAndGet();
            return response;
        }));

        assertThat(actual).isEqualTo(response);
        assertThat(executions).hasValue(0);
        verify(repository, never()).saveOutbox(any());
    }

    /** 같은 key를 다른 날짜에 재사용하면 ALREADY_EXISTS로 거절하는지 검증한다. */
    @Test
    void executeRejectsTheSameKeyForAnotherRequest() {
        FinalizeDailyRankingRequest original = request("2026-07-03", KEY);
        when(repository.findRecord(ACTOR, OPERATION, KEY))
                .thenReturn(Optional.of(record(original, response("2026-07-03", 2))));

        assertThatThrownBy(() -> inContext(
                KEY,
                () -> executor.execute(request("2026-07-04", KEY), () -> response("2026-07-04", 1))))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(exception -> assertThat(((StatusRuntimeException) exception).getStatus().getCode())
                        .isEqualTo(Status.Code.ALREADY_EXISTS));
    }

    /** metadata와 request key가 다르면 명령 실행 전에 거절하는지 검증한다. */
    @Test
    void executeRejectsMismatchedKeys() {
        assertThatThrownBy(() -> inContext(
                KEY,
                () -> executor.execute(
                        request("2026-07-03", "22222222-2222-4222-8222-222222222222"),
                        () -> response("2026-07-03", 1))))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(exception -> assertThat(((StatusRuntimeException) exception).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    /** Outbox 저장 실패 시 성공 응답을 저장하지 않고 transaction을 rollback하는지 검증한다. */
    @Test
    void executeRollsBackWhenOutboxSaveFails() {
        FinalizeDailyRankingRequest request = request("2026-07-03", KEY);
        when(repository.findRecord(ACTOR, OPERATION, KEY)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new IllegalStateException("outbox failed"))
                .when(repository).saveOutbox(any());

        assertThatThrownBy(() -> inContext(
                KEY, () -> executor.execute(request, () -> response("2026-07-03", 2))))
                .isInstanceOf(IllegalStateException.class);

        verify(repository, never()).saveRecord(any());
        verify(transactionManager).rollback(transactionStatus);
    }

    /** 동시에 같은 key가 저장되면 먼저 commit한 요청의 응답을 재생하는지 검증한다. */
    @Test
    void executeReplaysTheWinnerAfterAConcurrentInsert() throws Exception {
        FinalizeDailyRankingRequest request = request("2026-07-03", KEY);
        FinalizeDailyRankingResponse response = response("2026-07-03", 2);
        when(repository.findRecord(ACTOR, OPERATION, KEY))
                .thenReturn(Optional.empty(), Optional.of(record(request, response)));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("race"))
                .when(repository).saveRecord(any());

        FinalizeDailyRankingResponse actual = inContext(
                KEY, () -> executor.execute(request, () -> response));

        assertThat(actual).isEqualTo(response);
        verify(transactionManager).rollback(transactionStatus);
    }

    /** TTL 정리 작업이 현재 시각 이전의 record 삭제를 요청하는지 검증한다. */
    @Test
    void cleanExpiredRecordsUsesTheInjectedClock() {
        executor.cleanExpiredRecords();

        verify(repository).deleteExpiredRecords(Instant.parse("2026-07-03T06:30:00Z"));
    }

    /** 테스트용 gRPC Context 안에서 명령을 실행한다. */
    private <T> T inContext(String key, java.util.concurrent.Callable<T> command) throws Exception {
        IdempotencyContext context = new IdempotencyContext(ACTOR, OPERATION, key);
        return Context.current().withValue(IdempotencyContext.CONTEXT_KEY, context).call(command);
    }

    /** 테스트용 Finalize 요청을 만든다. */
    private FinalizeDailyRankingRequest request(String date, String key) {
        return FinalizeDailyRankingRequest.newBuilder()
                .setRankingDate(date)
                .setCommandMetadata(CommandMetadata.newBuilder().setIdempotencyKey(key))
                .build();
    }

    /** 테스트용 Finalize 응답을 만든다. */
    private FinalizeDailyRankingResponse response(String date, int count) {
        return FinalizeDailyRankingResponse.newBuilder()
                .setRankingDate(date)
                .setRankedUserCount(count)
                .build();
    }

    /** 저장 응답 재생에 사용할 멱등성 record를 만든다. */
    private RankingCommandRepository.IdempotencyRecord record(
            FinalizeDailyRankingRequest request,
            FinalizeDailyRankingResponse response) {
        return new RankingCommandRepository.IdempotencyRecord(
                ACTOR,
                OPERATION,
                KEY,
                requestHasher.hash(OPERATION, ACTOR, request),
                response.toByteArray(),
                response.getDescriptorForType().getFullName(),
                Instant.parse("2026-07-04T06:30:00Z"));
    }
}

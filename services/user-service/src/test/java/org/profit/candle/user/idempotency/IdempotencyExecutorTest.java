package org.profit.candle.user.idempotency;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.proto.user.v1.UpdateProfileRequest;
import org.profit.candle.proto.user.v1.UpdateProfileResponse;
import org.profit.candle.user.idempotency.entity.IdempotencyRecord;
import org.profit.candle.user.idempotency.entity.IdempotencyRecordId;
import org.profit.candle.user.idempotency.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyExecutorTest {

    @Mock IdempotencyRecordRepository repository;
    @Mock RequestHasher hasher;
    @Mock PlatformTransactionManager transactionManager;

    IdempotencyExecutor executor;

    private static final String ACTOR = "user-1";
    private static final String OPERATION = "candle.user.v1.UserService/UpdateProfile";
    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String HASH = "abc123hash";

    private final UpdateProfileRequest request = UpdateProfileRequest.newBuilder()
            .setUserId(ACTOR).setNickname("nick").build();

    private final UpdateProfileResponse response = UpdateProfileResponse.newBuilder().build();

    @BeforeEach
    void setUp() {
        executor = new IdempotencyExecutor(repository, hasher, transactionManager, Duration.ofDays(1));
        lenient().when(hasher.hash(any(), any(), any())).thenReturn(HASH);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private void runInContext(Runnable action) {
        IdempotencyContext ctx = new IdempotencyContext(ACTOR, OPERATION, KEY);
        Context.current().withValue(IdempotencyContext.CONTEXT_KEY, ctx).run(action);
    }

    @Test
    void execute_newRequest_runsCommandAndSavesRecord() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        runInContext(() -> {
            UpdateProfileResponse result = executor.execute(request, UpdateProfileResponse.parser(), () -> response);
            assertThat(result).isEqualTo(response);
        });

        verify(repository).saveAndFlush(any(IdempotencyRecord.class));
    }

    @Test
    void execute_existingRecordSameHash_replaysResponse() throws Exception {
        byte[] payload = response.toByteArray();
        IdempotencyRecord existing = new IdempotencyRecord(
                new IdempotencyRecordId(ACTOR, OPERATION, KEY),
                HASH, payload, "candle.user.v1.UpdateProfileResponse", "OK",
                Instant.now().plus(Duration.ofDays(1)));
        when(repository.findById(any())).thenReturn(Optional.of(existing));

        runInContext(() -> {
            UpdateProfileResponse result = executor.execute(request, UpdateProfileResponse.parser(), () -> {
                throw new AssertionError("command should not run on replay");
            });
            assertThat(result).isEqualTo(response);
        });

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void execute_existingRecordDifferentHash_throwsAlreadyExists() {
        IdempotencyRecord existing = new IdempotencyRecord(
                new IdempotencyRecordId(ACTOR, OPERATION, KEY),
                "different-hash", new byte[0], "type", "OK",
                Instant.now().plus(Duration.ofDays(1)));
        when(repository.findById(any())).thenReturn(Optional.of(existing));

        runInContext(() ->
                assertThatThrownBy(() -> executor.execute(request, UpdateProfileResponse.parser(), () -> response))
                        .isInstanceOf(StatusRuntimeException.class)
                        .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                                .isEqualTo(Status.Code.ALREADY_EXISTS))
        );
    }

    @Test
    void execute_nullIdempotencyKey_throwsInvalidArgument() {
        IdempotencyContext ctx = new IdempotencyContext(ACTOR, OPERATION, null);
        Context.current().withValue(IdempotencyContext.CONTEXT_KEY, ctx).run(() ->
                assertThatThrownBy(() -> executor.execute(request, UpdateProfileResponse.parser(), () -> response))
                        .isInstanceOf(StatusRuntimeException.class)
                        .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                                .isEqualTo(Status.Code.INVALID_ARGUMENT))
        );
    }

    @Test
    void execute_blankActorId_throwsUnauthenticated() {
        IdempotencyContext ctx = new IdempotencyContext("", OPERATION, KEY);
        Context.current().withValue(IdempotencyContext.CONTEXT_KEY, ctx).run(() ->
                assertThatThrownBy(() -> executor.execute(request, UpdateProfileResponse.parser(), () -> response))
                        .isInstanceOf(StatusRuntimeException.class)
                        .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                                .isEqualTo(Status.Code.UNAUTHENTICATED))
        );
    }

    @Test
    void execute_nullContext_throwsInvalidArgument() {
        // No context attached → IdempotencyContext.current() == null
        assertThatThrownBy(() -> executor.execute(request, UpdateProfileResponse.parser(), () -> response))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void execute_raceCondition_replaysWinnersResponse() throws Exception {
        byte[] payload = response.toByteArray();
        IdempotencyRecord winner = new IdempotencyRecord(
                new IdempotencyRecordId(ACTOR, OPERATION, KEY),
                HASH, payload, "type", "OK",
                Instant.now().plus(Duration.ofDays(1)));

        when(repository.findById(any()))
                .thenReturn(Optional.empty())   // first lookup: not found
                .thenReturn(Optional.of(winner)); // second lookup after race
        when(transactionManager.getTransaction(any())).thenThrow(new DataIntegrityViolationException("race"));

        runInContext(() -> {
            UpdateProfileResponse result = executor.execute(request, UpdateProfileResponse.parser(), () -> response);
            assertThat(result).isEqualTo(response);
        });
    }
}

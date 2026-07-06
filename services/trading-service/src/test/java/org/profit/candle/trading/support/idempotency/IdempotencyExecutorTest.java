package org.profit.candle.trading.support.idempotency;

import com.google.protobuf.Parser;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.proto.trading.v1.Order;
import org.profit.candle.proto.trading.v1.OrderSide;
import org.profit.candle.proto.trading.v1.OrderKind;
import org.profit.candle.proto.trading.v1.PlaceOrderRequest;
import org.profit.candle.proto.trading.v1.PlaceOrderResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * IdempotencyExecutor 단위 테스트 — 멱등성 처리 알고리즘(스펙 §5)의 4단계
 * (hash 계산 → 조회 → 트랜잭션 실행 → race 시 재조회) 각 분기를 검증한다.
 *
 * <p>실제 DB 트랜잭션은 필요 없으므로 PlatformTransactionManager는 mock으로 대체하되,
 * TransactionTemplate.execute()가 실제로 콜백을 호출하도록 default mock 동작을 그대로 쓴다
 * (getTransaction()이 null을 반환해도 콜백 람다는 status 파라미터를 쓰지 않으므로 안전하다).</p>
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyExecutorTest {

    private record FakeRecord(String requestHash, byte[] responsePayload) {}

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private IdempotencyOperations<String, FakeRecord> ops;

    private final RequestHasher hasher = new RequestHasher();
    private IdempotencyExecutor executor;

    private static final String ACTOR = "11111111-1111-1111-1111-111111111111";
    private static final String OPERATION = "OrderService/PlaceOrder";
    private static final String IDEMPOTENCY_KEY = "idem-key-1";
    private static final String RECORD_ID = "record-1";

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        executor = new IdempotencyExecutor(hasher, transactionManager, Duration.ofDays(1));
    }

    private <T> T withContext(IdempotencyContext context, java.util.function.Supplier<T> body) {
        Context grpcContext = Context.current().withValue(IdempotencyContext.CONTEXT_KEY, context);
        Context previous = grpcContext.attach();
        try {
            return body.get();
        } finally {
            grpcContext.detach(previous);
        }
    }

    private PlaceOrderRequest request(String symbol) {
        return PlaceOrderRequest.newBuilder()
                .setUserId(ACTOR)
                .setSymbol(symbol)
                .setSide(OrderSide.ORDER_SIDE_BUY)
                .setKind(OrderKind.ORDER_KIND_LIMIT)
                .setQuantity(10)
                .setPrice(70_000)
                .build();
    }

    private PlaceOrderResponse response(String orderId) {
        return PlaceOrderResponse.newBuilder()
                .setOrder(Order.newBuilder().setId(orderId).build())
                .build();
    }

    private Parser<PlaceOrderResponse> parser() {
        return PlaceOrderResponse.parser();
    }

    @Nested
    @DisplayName("컨텍스트 검증")
    class ContextValidation {

        @Test
        void shouldRejectWhenIdempotencyKeyIsMissing() {
            IdempotencyContext context = new IdempotencyContext(ACTOR, OPERATION, "");

            assertThatThrownBy(() -> withContext(context, () -> executor.execute(
                    request("005930"), parser(), ops, () -> response("o1"))))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verifyNoInteractions(ops);
        }

        @Test
        void shouldRejectWhenActorIsMissing() {
            IdempotencyContext context = new IdempotencyContext("", OPERATION, IDEMPOTENCY_KEY);

            assertThatThrownBy(() -> withContext(context, () -> executor.execute(
                    request("005930"), parser(), ops, () -> response("o1"))))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);
            verifyNoInteractions(ops);
        }
    }

    @Nested
    @DisplayName("최초 요청 — 신규 실행")
    class FirstExecution {

        @Test
        void shouldExecuteCommandAndPersistRecordWhenNoExistingRecordFound() {
            IdempotencyContext context = new IdempotencyContext(ACTOR, OPERATION, IDEMPOTENCY_KEY);
            when(ops.newId(ACTOR, OPERATION, IDEMPOTENCY_KEY)).thenReturn(RECORD_ID);
            when(ops.findById(RECORD_ID)).thenReturn(Optional.empty());
            when(ops.newRecord(eq(RECORD_ID), anyString(), any(), anyString(), anyString(), any(Instant.class)))
                    .thenAnswer(inv -> new FakeRecord(inv.getArgument(1), inv.getArgument(2)));

            PlaceOrderResponse result = withContext(context, () ->
                    executor.execute(request("005930"), parser(), ops, () -> response("order-new")));

            assertThat(result.getOrder().getId()).isEqualTo("order-new");
            verify(ops).save(any(FakeRecord.class));
        }
    }

    @Nested
    @DisplayName("재수신 — 동일 key")
    class Replay {

        @Test
        void shouldReplayStoredResponseWhenSameKeyAndSameRequestHash() {
            IdempotencyContext context = new IdempotencyContext(ACTOR, OPERATION, IDEMPOTENCY_KEY);
            PlaceOrderRequest req = request("005930");
            String hash = hasher.hash(OPERATION, ACTOR, req);
            FakeRecord stored = new FakeRecord(hash, response("order-original").toByteArray());
            when(ops.newId(ACTOR, OPERATION, IDEMPOTENCY_KEY)).thenReturn(RECORD_ID);
            when(ops.findById(RECORD_ID)).thenReturn(Optional.of(stored));
            when(ops.requestHash(stored)).thenReturn(stored.requestHash());
            when(ops.responsePayload(stored)).thenReturn(stored.responsePayload());

            PlaceOrderResponse result = withContext(context, () ->
                    executor.execute(req, parser(), ops, () -> response("order-should-not-run")));

            // 저장된 응답을 그대로 재생 — command 람다는 호출되지 않아야 한다.
            assertThat(result.getOrder().getId()).isEqualTo("order-original");
            verify(ops, never()).save(any());
        }

        @Test
        void shouldThrowAlreadyExistsWhenSameKeyButDifferentRequestHash() {
            IdempotencyContext context = new IdempotencyContext(ACTOR, OPERATION, IDEMPOTENCY_KEY);
            FakeRecord stored = new FakeRecord("completely-different-hash",
                    response("order-original").toByteArray());
            when(ops.newId(ACTOR, OPERATION, IDEMPOTENCY_KEY)).thenReturn(RECORD_ID);
            when(ops.findById(RECORD_ID)).thenReturn(Optional.of(stored));
            when(ops.requestHash(stored)).thenReturn(stored.requestHash());

            assertThatThrownBy(() -> withContext(context, () -> executor.execute(
                    request("005930"), parser(), ops, () -> response("should-not-run"))))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.ALREADY_EXISTS);
        }

        @Test
        void shouldThrowInternalWhenStoredPayloadCannotBeParsed() {
            IdempotencyContext context = new IdempotencyContext(ACTOR, OPERATION, IDEMPOTENCY_KEY);
            PlaceOrderRequest req = request("005930");
            String hash = hasher.hash(OPERATION, ACTOR, req);
            byte[] corrupted = "이건 protobuf가 아님".getBytes(StandardCharsets.UTF_8);
            FakeRecord stored = new FakeRecord(hash, corrupted);
            when(ops.newId(ACTOR, OPERATION, IDEMPOTENCY_KEY)).thenReturn(RECORD_ID);
            when(ops.findById(RECORD_ID)).thenReturn(Optional.of(stored));
            when(ops.requestHash(stored)).thenReturn(stored.requestHash());
            when(ops.responsePayload(stored)).thenReturn(stored.responsePayload());

            assertThatThrownBy(() -> withContext(context, () ->
                    executor.execute(req, parser(), ops, () -> response("x"))))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.INTERNAL);
        }
    }

    @Nested
    @DisplayName("동시 요청 경합 (unique 제약 충돌)")
    class ConcurrentRace {

        @Test
        void shouldReplayWinnerRecordWhenConcurrentInsertLosesRace() {
            IdempotencyContext context = new IdempotencyContext(ACTOR, OPERATION, IDEMPOTENCY_KEY);
            PlaceOrderRequest req = request("005930");
            String hash = hasher.hash(OPERATION, ACTOR, req);
            FakeRecord winner = new FakeRecord(hash, response("order-winner").toByteArray());

            when(ops.newId(ACTOR, OPERATION, IDEMPOTENCY_KEY)).thenReturn(RECORD_ID);
            // 최초 조회 시엔 없음 → 트랜잭션 진입 → save 시점에 unique 위반 발생 상황을 재현.
            when(ops.findById(RECORD_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winner));
            when(ops.newRecord(eq(RECORD_ID), anyString(), any(), anyString(), anyString(), any(Instant.class)))
                    .thenAnswer(inv -> new FakeRecord(inv.getArgument(1), inv.getArgument(2)));
            doThrow(new DataIntegrityViolationException("duplicate key")).when(ops).save(any());
            when(ops.requestHash(winner)).thenReturn(winner.requestHash());
            when(ops.responsePayload(winner)).thenReturn(winner.responsePayload());

            PlaceOrderResponse result = withContext(context, () ->
                    executor.execute(req, parser(), ops, () -> response("order-loser")));

            assertThat(result.getOrder().getId()).isEqualTo("order-winner");
        }

        @Test
        void shouldPropagateOriginalExceptionWhenWinnerRecordNotFoundAfterRace() {
            IdempotencyContext context = new IdempotencyContext(ACTOR, OPERATION, IDEMPOTENCY_KEY);
            when(ops.newId(ACTOR, OPERATION, IDEMPOTENCY_KEY)).thenReturn(RECORD_ID);
            when(ops.findById(RECORD_ID)).thenReturn(Optional.empty()); // 재조회에도 계속 없음 — 이상 상황
            when(ops.newRecord(eq(RECORD_ID), anyString(), any(), anyString(), anyString(), any(Instant.class)))
                    .thenAnswer(inv -> new FakeRecord(inv.getArgument(1), inv.getArgument(2)));
            doThrow(new DataIntegrityViolationException("duplicate key")).when(ops).save(any());

            assertThatThrownBy(() -> withContext(context, () ->
                    executor.execute(request("005930"), parser(), ops, () -> response("x"))))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
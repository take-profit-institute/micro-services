package org.profit.candle.trading.support.idempotency;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * IdempotencyServerInterceptor 단위 테스트 (스펙 §6) — key 형식 검증과
 * IdempotencyContext 전파(actor/operation/idempotencyKey)를 검증한다.
 *
 * <p>IDEMPOTENCY_KEY/USER_ID가 package-private이라 같은 패키지에 테스트를 둔다.
 * Contexts.interceptCall()이 next.startCall() 호출 "이전"에 Context를 attach하므로,
 * next.startCall()의 Answer 안에서 IdempotencyContext.current()를 읽어 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServerInterceptorTest {

    @Mock private ServerCall<Object, Object> call;
    @Mock private ServerCallHandler<Object, Object> next;
    @Mock private ServerCall.Listener<Object> nextListener;
    @Mock private MethodDescriptor<Object, Object> methodDescriptor;

    private final IdempotencyServerInterceptor interceptor = new IdempotencyServerInterceptor();

    @BeforeEach
    void setUp() {
        lenient().when(call.getMethodDescriptor()).thenReturn(methodDescriptor);
        lenient().when(methodDescriptor.getFullMethodName()).thenReturn("trading.v1.OrderService/PlaceOrder");
    }

    @Nested
    @DisplayName("idempotency key 형식 검증")
    class KeyValidation {

        @Test
        void shouldRejectMalformedIdempotencyKeyWithInvalidArgument() {
            Metadata headers = new Metadata();
            headers.put(IdempotencyServerInterceptor.IDEMPOTENCY_KEY, "not-a-uuid");

            ServerCall.Listener<Object> result = interceptor.interceptCall(call, headers, next);

            ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
            verify(call).close(statusCaptor.capture(), any());
            assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(result).isNotNull();
            verifyNoInteractions(next);
        }

        @Test
        void shouldRejectKeyLongerThanMaxLength() {
            Metadata headers = new Metadata();
            headers.put(IdempotencyServerInterceptor.IDEMPOTENCY_KEY, "a".repeat(100));

            interceptor.interceptCall(call, headers, next);

            ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
            verify(call).close(statusCaptor.capture(), any());
            assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        }

        @Test
        void shouldAllowMissingIdempotencyKeyForReadOnlyCalls() {
            Metadata headers = new Metadata();
            when(next.startCall(any(), any())).thenReturn(nextListener);

            interceptor.interceptCall(call, headers, next);

            verify(next).startCall(eq(call), eq(headers));
            verify(call, never()).close(any(), any());
        }

        @Test
        void shouldAcceptValidCanonicalUuidKey() {
            Metadata headers = new Metadata();
            headers.put(IdempotencyServerInterceptor.IDEMPOTENCY_KEY,
                    "11111111-1111-1111-1111-111111111111");
            when(next.startCall(any(), any())).thenReturn(nextListener);

            interceptor.interceptCall(call, headers, next);

            verify(next).startCall(any(), any());
            verify(call, never()).close(any(), any());
        }
    }

    @Nested
    @DisplayName("IdempotencyContext 전파")
    class ContextPropagation {

        @Test
        void shouldAttachContextWithActorIdempotencyKeyAndOperationBeforeInvokingNext() {
            Metadata headers = new Metadata();
            headers.put(IdempotencyServerInterceptor.IDEMPOTENCY_KEY,
                    "22222222-2222-2222-2222-222222222222");
            headers.put(IdempotencyServerInterceptor.USER_ID, "user-abc");

            IdempotencyContext[] captured = new IdempotencyContext[1];
            when(next.startCall(any(), any())).thenAnswer(invocation -> {
                captured[0] = IdempotencyContext.current();
                return nextListener;
            });

            interceptor.interceptCall(call, headers, next);

            assertThat(captured[0]).isNotNull();
            assertThat(captured[0].actorId()).isEqualTo("user-abc");
            assertThat(captured[0].idempotencyKey()).isEqualTo("22222222-2222-2222-2222-222222222222");
            assertThat(captured[0].operation()).isEqualTo("trading.v1.OrderService/PlaceOrder");
        }

        @Test
        void shouldPropagateNullActorIdWhenUserIdHeaderMissing() {
            Metadata headers = new Metadata();
            IdempotencyContext[] captured = new IdempotencyContext[1];
            when(next.startCall(any(), any())).thenAnswer(invocation -> {
                captured[0] = IdempotencyContext.current();
                return nextListener;
            });

            interceptor.interceptCall(call, headers, next);

            assertThat(captured[0].actorId()).isNull();
        }

        @Test
        void shouldPropagateNullIdempotencyKeyWhenHeaderMissing() {
            Metadata headers = new Metadata();
            headers.put(IdempotencyServerInterceptor.USER_ID, "user-abc");
            IdempotencyContext[] captured = new IdempotencyContext[1];
            when(next.startCall(any(), any())).thenAnswer(invocation -> {
                captured[0] = IdempotencyContext.current();
                return nextListener;
            });

            interceptor.interceptCall(call, headers, next);

            assertThat(captured[0].idempotencyKey()).isNull();
        }

        @Test
        void shouldNotLeakContextAfterInterceptCallReturns() {
            Metadata headers = new Metadata();
            headers.put(IdempotencyServerInterceptor.IDEMPOTENCY_KEY,
                    "33333333-3333-3333-3333-333333333333");
            when(next.startCall(any(), any())).thenReturn(nextListener);

            interceptor.interceptCall(call, headers, next);

            // Contexts.interceptCall은 next.startCall() 호출 동안만 context를 attach하고
            // 리턴 직후엔 detach한다 — 호출 스레드에 context가 계속 남아있으면 안 된다.
            assertThat(IdempotencyContext.current()).isNull();
        }
    }
}
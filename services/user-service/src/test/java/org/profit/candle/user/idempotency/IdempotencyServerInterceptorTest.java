package org.profit.candle.user.idempotency;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServerInterceptorTest {

    @Mock ServerCall<Object, Object> serverCall;
    @Mock ServerCallHandler<Object, Object> serverCallHandler;
    @Mock MethodDescriptor<Object, Object> methodDescriptor;

    IdempotencyServerInterceptor interceptor;

    private static final String VALID_KEY = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {
        interceptor = new IdempotencyServerInterceptor();
        lenient().when(serverCall.getMethodDescriptor()).thenReturn(methodDescriptor);
        lenient().when(methodDescriptor.getFullMethodName()).thenReturn("candle.user.v1.UserService/UpdateProfile");
        lenient().when(serverCallHandler.startCall(any(), any())).thenReturn(new ServerCall.Listener<>() {});
    }

    @Test
    void interceptCall_noIdempotencyKey_proceedsWithNullKey() {
        Metadata headers = new Metadata();
        headers.put(IdempotencyServerInterceptor.USER_ID, "user-1");

        interceptor.interceptCall(serverCall, headers, serverCallHandler);

        verify(serverCallHandler).startCall(any(), any());
        verify(serverCall, never()).close(any(), any());
    }

    @Test
    void interceptCall_validKey_proceedsAndPopulatesContext() {
        Metadata headers = new Metadata();
        headers.put(IdempotencyServerInterceptor.IDEMPOTENCY_KEY, VALID_KEY);
        headers.put(IdempotencyServerInterceptor.USER_ID, "user-1");

        interceptor.interceptCall(serverCall, headers, serverCallHandler);

        verify(serverCallHandler).startCall(any(), any());
        verify(serverCall, never()).close(any(), any());
    }

    @Test
    void interceptCall_invalidKeyFormat_closesWithInvalidArgument() {
        Metadata headers = new Metadata();
        headers.put(IdempotencyServerInterceptor.IDEMPOTENCY_KEY, "not-a-uuid");

        interceptor.interceptCall(serverCall, headers, serverCallHandler);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(statusCaptor.capture(), any());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(statusCaptor.getValue().getDescription()).isEqualTo("IDEMPOTENCY_KEY_INVALID");
        verify(serverCallHandler, never()).startCall(any(), any());
    }

    @Test
    void interceptCall_keyTooLong_closesWithInvalidArgument() {
        String tooLong = "550e8400-e29b-41d4-a716-446655440000-extra-padding-that-exceeds-64-chars";
        Metadata headers = new Metadata();
        headers.put(IdempotencyServerInterceptor.IDEMPOTENCY_KEY, tooLong);

        interceptor.interceptCall(serverCall, headers, serverCallHandler);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(statusCaptor.capture(), any());
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void interceptCall_noUserId_proceedsWithNullActorId() {
        Metadata headers = new Metadata();
        headers.put(IdempotencyServerInterceptor.IDEMPOTENCY_KEY, VALID_KEY);
        // no USER_ID header

        interceptor.interceptCall(serverCall, headers, serverCallHandler);

        verify(serverCallHandler).startCall(any(), any());
    }
}

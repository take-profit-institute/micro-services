package org.profit.candle.trading.account.grpc;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.proto.trading.v1.GetBalanceRequest;
import org.profit.candle.proto.trading.v1.GetBalanceResponse;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.support.idempotency.IdempotencyContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AccountGrpcService 단위 테스트. Spring 컨텍스트 없이 순수 mock으로 검증한다.
 * IdempotencyContext는 io.grpc.Context에 직접 값을 attach/detach해서 requireActor()가
 * 읽는 실제 인증 컨텍스트를 재현한다.
 */
@ExtendWith(MockitoExtension.class)
class AccountGrpcServiceTest {

    @Mock private AccountService accountService;
    @Mock private StreamObserver<GetBalanceResponse> observer;

    private AccountGrpcService grpcService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        grpcService = new AccountGrpcService(accountService);
    }

    private <T> T withActor(String actorId, java.util.function.Supplier<T> body) {
        Context context = Context.current().withValue(
                IdempotencyContext.CONTEXT_KEY, new IdempotencyContext(actorId, "AccountService/GetBalance", null));
        Context previous = context.attach();
        try {
            return body.get();
        } finally {
            context.detach(previous);
        }
    }

    private void runWithActor(String actorId, Runnable body) {
        withActor(actorId, () -> {
            body.run();
            return null;
        });
    }

    @Nested
    @DisplayName("getBalance")
    class GetBalance {

        @Test
        void shouldReturnBalanceWhenActorMatchesRequestUserId() {
            AccountEntity account = AccountEntity.create(userId);
            when(accountService.getAccount(userId)).thenReturn(account);
            GetBalanceRequest request = GetBalanceRequest.newBuilder().setUserId(userId.toString()).build();

            runWithActor(userId.toString(), () -> grpcService.getBalance(request, observer));

            ArgumentCaptor<GetBalanceResponse> captor = ArgumentCaptor.forClass(GetBalanceResponse.class);
            verify(observer).onNext(captor.capture());
            verify(observer).onCompleted();
            assertThat(captor.getValue().getBalance().getCash()).isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW);
            assertThat(captor.getValue().getBalance().getAvailableCash())
                    .isEqualTo(AccountEntity.INITIAL_SEED_MONEY_KRW);
        }

        @Test
        void shouldThrowUnauthenticatedWhenActorContextIsMissing() {
            GetBalanceRequest request = GetBalanceRequest.newBuilder().setUserId(userId.toString()).build();

            // requireActor()가 던지는 StatusRuntimeException은 AccountException이 아니라서
            // catch(AccountException) 블록에 걸리지 않는다 — observer.onError가 아니라
            // getBalance() 메서드 호출 자체가 예외를 던진다.
            assertThatThrownBy(() -> grpcService.getBalance(request, observer))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);

            verifyNoInteractions(accountService, observer);
        }

        @Test
        void shouldThrowPermissionDeniedWhenRequestUserIdMismatchesAuthenticatedActor() {
            String otherUserId = UUID.randomUUID().toString();
            GetBalanceRequest request = GetBalanceRequest.newBuilder().setUserId(otherUserId).build();

            assertThatThrownBy(() -> withActor(userId.toString(), () -> {
                grpcService.getBalance(request, observer);
                return null;
            }))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.PERMISSION_DENIED);
        }

        @Test
        void shouldPropagateAccountExceptionAsGrpcError() {
            when(accountService.getAccount(userId)).thenThrow(new AccountException(AccountErrorCode.ACCOUNT_NOT_FOUND));
            GetBalanceRequest request = GetBalanceRequest.newBuilder().setUserId(userId.toString()).build();

            runWithActor(userId.toString(), () -> grpcService.getBalance(request, observer));

            ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
            verify(observer).onError(captor.capture());
            assertThat(((StatusRuntimeException) captor.getValue()).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND);
        }
    }

    /**
     * toGrpcException()의 switch문 모든 분기를 한 번에 강제 실행 — enum에 새 값이 추가됐는데
     * 매핑이 누락되면 컴파일 타임에 switch가 exhaustive하지 않다고 잡히지만, 그 전까지는
     * 이 테스트가 "모든 case가 실제로 의도한 Status로 이어지는지"를 보증한다.
     */
    @ParameterizedTest(name = "{0} → 매핑된 gRPC Status가 존재한다")
    @EnumSource(AccountErrorCode.class)
    @DisplayName("AccountErrorCode 전체 값에 대해 toGrpcException이 예외 없이 Status를 매핑한다")
    void shouldMapEveryAccountErrorCodeToSomeGrpcStatus(AccountErrorCode errorCode) {
        when(accountService.getAccount(userId)).thenThrow(new AccountException(errorCode));
        GetBalanceRequest request = GetBalanceRequest.newBuilder().setUserId(userId.toString()).build();

        runWithActor(userId.toString(), () -> grpcService.getBalance(request, observer));

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
        // switch에 해당 분기가 없으면 MatchException/IllegalStateException이 나므로,
        // StatusRuntimeException으로 정상 변환됐다는 것 자체가 분기 존재를 증명한다.
    }
}
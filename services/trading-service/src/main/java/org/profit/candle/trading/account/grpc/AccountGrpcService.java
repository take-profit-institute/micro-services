package org.profit.candle.trading.account.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.proto.trading.v1.AccountBalance;
import org.profit.candle.proto.trading.v1.AccountServiceGrpc;
import org.profit.candle.proto.trading.v1.GetBalanceRequest;
import org.profit.candle.proto.trading.v1.GetBalanceResponse;
import org.profit.candle.trading.account.entity.AccountBalanceEntity;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.support.idempotency.IdempotencyContext;
import org.springframework.stereotype.Component;

/**
 * AccountService gRPC 엔드포인트.
 *
 * 읽기 전용 RPC라 멱등성 처리 대상이 아니다. actor 검증 후 바로 조회한다.
 */
@Component
@RequiredArgsConstructor
public class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {

    private final AccountService accountService;

    @Override
    public void getBalance(GetBalanceRequest request, StreamObserver<GetBalanceResponse> observer) {
        String actor = requireActor(request.getUserId());
        AccountBalanceEntity balance = accountService.getBalance(actor);
        observer.onNext(GetBalanceResponse.newBuilder().setBalance(toProto(balance)).build());
        observer.onCompleted();
    }

    // ── actor 검증 (인증값은 metadata만 신뢰, request.user_id는 일치만 검사) ──
    private String requireActor(String requestUserId) {
        IdempotencyContext context = IdempotencyContext.current();
        String actor = context == null ? null : context.actorId();
        if (actor == null || actor.isBlank()) {
            throw Status.UNAUTHENTICATED.withDescription("MISSING_ACTOR").asRuntimeException();
        }
        if (requestUserId != null && !requestUserId.isBlank() && !requestUserId.equals(actor)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("user_id does not match authenticated actor")
                    .asRuntimeException();
        }
        return actor;
    }

    // ── 매핑 ──────────────────────────────────────────────────────────
    private AccountBalance toProto(AccountBalanceEntity balance) {
        return AccountBalance.newBuilder()
                .setUserId(balance.userId())
                .setCash(balance.cash())
                .setReservedBalance(balance.reservedBalance())
                .setAvailableCash(balance.availableCash())
                .setTotalAsset(balance.cash())
                .build();
    }
}
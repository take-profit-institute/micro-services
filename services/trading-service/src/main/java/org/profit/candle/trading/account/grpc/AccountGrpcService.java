package org.profit.candle.trading.account.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.proto.trading.v1.AccountBalance;
import org.profit.candle.proto.trading.v1.AccountServiceGrpc;
import org.profit.candle.proto.trading.v1.GetBalanceRequest;
import org.profit.candle.proto.trading.v1.GetBalanceResponse;
import org.profit.candle.trading.account.entity.AccountEntity;
import org.profit.candle.trading.account.exception.AccountErrorCode;
import org.profit.candle.trading.account.exception.AccountException;
import org.profit.candle.trading.account.service.AccountService;
import org.profit.candle.trading.support.idempotency.IdempotencyContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
        try {
            UUID actor = requireActor(request.getUserId());
            AccountEntity account = accountService.getAccount(actor);
            observer.onNext(GetBalanceResponse.newBuilder().setBalance(toProto(account)).build());
            observer.onCompleted();
        } catch (AccountException e) {
            observer.onError(toGrpcException((AccountErrorCode) e.errorCode()));
        }
    }

    /**
     * AccountErrorCode → gRPC Status 매핑.
     *
     * TODO: support/ 공통 변환 계층(인터셉터/AOP) 도입 시 이 메서드 제거.
     * 관련 제안 이슈: #{이슈번호}
     *
     * 매핑 기준(BFF-마이크로서비스 gRPC 연동 명세 2.3절 대응):
     * - 입력값 자체가 잘못됨 → INVALID_ARGUMENT
     * - 리소스가 없음 → NOT_FOUND
     * - 상태/잔고 등 현재 조건상 처리 불가 → FAILED_PRECONDITION
     */
    private StatusRuntimeException toGrpcException(AccountErrorCode errorCode) {
        Status status = switch (errorCode) {
            case INVALID_LOCK_AMOUNT, INVALID_RELEASE_AMOUNT ->
                    Status.INVALID_ARGUMENT;
            case ACCOUNT_NOT_FOUND ->
                    Status.NOT_FOUND;
            case INSUFFICIENT_LOCKED_BALANCE, INSUFFICIENT_AVAILABLE_BALANCE,
                 INSUFFICIENT_CASH_BALANCE, ACCOUNT_INACTIVE ->
                    Status.FAILED_PRECONDITION;
        };
        return status.withDescription(errorCode.message()).asRuntimeException();
    }


    // ── actor 검증 (인증값은 metadata만 신뢰, request.user_id는 일치만 검사) ──
    private UUID requireActor(String requestUserId) {
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
        return UUID.fromString(actor);
    }

    // ── 매핑 ──────────────────────────────────────────────────────────
    private AccountBalance toProto(AccountEntity account) {
        // total_asset = 계좌 잔고 + 보유 종목 평가금액(랭킹 정책 1번)이어야 하지만,
        // Holdings는 본 도메인 소유가 아니라 여기서 계산할 수 없다.
        // 현재는 cash_krw로 채우되, 이는 부정확한 임시값이다 — BFF/Portfolio 쪽에서
        // Holdings 평가금액을 합산해 보정하거나, 이 필드 채움 책임을 재논의해야 한다.
        return AccountBalance.newBuilder()
                .setUserId(account.getUserId().toString())
                .setCash(account.getCashKrw())
                .setReservedBalance(account.getLockedKrw())
                .setAvailableCash(account.availableKrw())
                .setTotalAsset(account.getCashKrw())
                .build();
    }
}
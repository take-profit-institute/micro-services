package org.profit.candle.user.profile.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.proto.user.v1.GetMeRequest;
import org.profit.candle.proto.user.v1.GetMeResponse;
import org.profit.candle.proto.user.v1.ListAdminUsersRequest;
import org.profit.candle.proto.user.v1.ListAdminUsersResponse;
import org.profit.candle.proto.user.v1.UpdateProfileRequest;
import org.profit.candle.proto.user.v1.UpdateProfileResponse;
import org.profit.candle.proto.user.v1.UserProfile;
import org.profit.candle.proto.user.v1.UserServiceGrpc;
import org.profit.candle.proto.common.v1.Audit;
import org.profit.candle.user.idempotency.IdempotencyContext;
import org.profit.candle.user.idempotency.IdempotencyExecutor;
import org.profit.candle.user.profile.dto.UpdateProfileCommand;
import org.profit.candle.user.profile.dto.UserPageResult;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.profit.candle.user.profile.dto.UserSearchQuery;
import org.profit.candle.user.profile.exception.UserErrorCode;
import org.profit.candle.user.profile.service.UserProfileService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserProfileService userProfileService;
    private final IdempotencyExecutor idempotencyExecutor;

    @Override
    public void getMe(GetMeRequest request, StreamObserver<GetMeResponse> observer) {
        String actor = requireActor(request.getUserId());
        try {
            UserProfileResult result = userProfileService.getProfile(actor);
            observer.onNext(GetMeResponse.newBuilder().setProfile(toProto(result)).build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<UpdateProfileResponse> observer) {
        String actor = requireActor(request.getUserId());
        var command = new UpdateProfileCommand(
                blankToNull(request.getNickname()),
                blankToNull(request.getProfileImageUrl()));

        try {
            UpdateProfileResponse response = idempotencyExecutor.execute(
                    request,
                    UpdateProfileResponse.parser(),
                    () -> {
                        UserProfileResult result = userProfileService.updateProfile(actor, command);
                        return UpdateProfileResponse.newBuilder().setProfile(toProto(result)).build();
                    });
            observer.onNext(response);
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    /**
     * 관리자 콘솔 전용 회원 목록.
     *
     * <p>{@link #getMe}/{@link #updateProfile}와 달리 <b>actor 일치 검증을 하지 않는다</b> —
     * 관리자는 정의상 남의 프로필을 본다. 인가는 BFF의 admin 라우트 가드(ADMIN|SUPER_ADMIN JWT)가
     * 담당하며, 이 gRPC 포트는 클러스터 내부에만 열려 있다. learning-service의 ListAdminContents와
     * 같은 신뢰 경계다. 이 RPC를 외부에 직접 노출하게 되면 여기에 role 검증을 추가해야 한다.
     */
    @Override
    public void listAdminUsers(ListAdminUsersRequest request, StreamObserver<ListAdminUsersResponse> observer) {
        // proto3 기본값(빈 문자열/0)이 곧 "미지정"이다. 정규화는 UserSearchQuery가 맡는다.
        var query = new UserSearchQuery(
                request.getQuery(),
                request.hasDeleted() ? request.getDeleted() : null,
                request.getPage(),
                request.getSize());

        try {
            UserPageResult result = userProfileService.listUsers(query);
            ListAdminUsersResponse.Builder response = ListAdminUsersResponse.newBuilder()
                    .setTotalCount((int) result.totalCount())
                    .setPage(result.page())
                    .setSize(result.size());
            result.users().forEach(user -> response.addUsers(toProto(user)));
            observer.onNext(response.build());
            observer.onCompleted();
        } catch (CandleException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    // ── actor 검증 ────────────────────────────────────────────────────
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

    // ── 매핑 ─────────────────────────────────────────────────────────
    private UserProfile toProto(UserProfileResult result) {
        UserProfile.Builder builder = UserProfile.newBuilder()
                .setUserId(result.userId())
                .setDeleted(result.deleted());

        if (result.email() != null) builder.setEmail(result.email());
        if (result.nickname() != null) builder.setNickname(result.nickname());
        if (result.profileImageUrl() != null) builder.setProfileImageUrl(result.profileImageUrl());

        Audit.Builder audit = Audit.newBuilder().setVersion(result.version());
        if (result.createdAt() != null) audit.setCreatedAt(toTimestamp(result.createdAt()));
        if (result.updatedAt() != null) audit.setUpdatedAt(toTimestamp(result.updatedAt()));
        builder.setAudit(audit.build());

        return builder.build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private Status toGrpcStatus(CandleException e) {
        String code = e.errorCode().code();
        if (code.equals(UserErrorCode.USER_NOT_FOUND.code())) {
            return Status.NOT_FOUND.withDescription(code);
        }
        return Status.INVALID_ARGUMENT.withDescription(code);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

package org.profit.candle.user.profile.grpc;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.common.error.CandleException;
import org.profit.candle.proto.user.v1.GetMeRequest;
import org.profit.candle.proto.user.v1.GetMeResponse;
import org.profit.candle.proto.user.v1.UpdateProfileRequest;
import org.profit.candle.proto.user.v1.UpdateProfileResponse;
import org.profit.candle.user.idempotency.IdempotencyContext;
import org.profit.candle.user.idempotency.IdempotencyExecutor;
import org.profit.candle.user.profile.dto.UpdateProfileCommand;
import org.profit.candle.user.profile.dto.UserProfileResult;
import org.profit.candle.user.profile.exception.UserErrorCode;
import org.profit.candle.user.profile.service.UserProfileService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGrpcServiceTest {

    @Mock UserProfileService userProfileService;
    @Mock IdempotencyExecutor idempotencyExecutor;

    UserGrpcService service;

    private static final String ACTOR = "user-1";
    private static final String OPERATION = "candle.user.v1.UserService/GetMe";

    @BeforeEach
    void setUp() {
        service = new UserGrpcService(userProfileService, idempotencyExecutor);
    }

    private UserProfileResult profile(String userId) {
        return new UserProfileResult(userId, "a@b.com", "nick", "url", false, Instant.now(), Instant.now(), 0L);
    }

    private void runWithActor(String actorId, Runnable action) {
        IdempotencyContext ctx = new IdempotencyContext(actorId, OPERATION, null);
        Context.current().withValue(IdempotencyContext.CONTEXT_KEY, ctx).run(action);
    }

    // ─── getMe ───────────────────────────────────────────────────────────────

    @Test
    void getMe_happyPath_returnsProfile() {
        when(userProfileService.getProfile(ACTOR)).thenReturn(profile(ACTOR));
        CapturingObserver<GetMeResponse> observer = new CapturingObserver<>();

        runWithActor(ACTOR, () ->
                service.getMe(GetMeRequest.newBuilder().setUserId(ACTOR).build(), observer));

        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.value.getProfile().getUserId()).isEqualTo(ACTOR);
        assertThat(observer.value.getProfile().getEmail()).isEqualTo("a@b.com");
    }

    @Test
    void getMe_userNotFound_callsOnErrorWithNotFound() {
        when(userProfileService.getProfile(ACTOR))
                .thenThrow(new CandleException(UserErrorCode.USER_NOT_FOUND));
        CapturingObserver<GetMeResponse> observer = new CapturingObserver<>();

        runWithActor(ACTOR, () ->
                service.getMe(GetMeRequest.newBuilder().build(), observer));

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void getMe_missingActor_throwsUnauthenticated() {
        IdempotencyContext ctx = new IdempotencyContext(null, OPERATION, null);
        CapturingObserver<GetMeResponse> observer = new CapturingObserver<>();

        assertThatThrownBy(() ->
                Context.current().withValue(IdempotencyContext.CONTEXT_KEY, ctx).run(() ->
                        service.getMe(GetMeRequest.newBuilder().build(), observer)))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.UNAUTHENTICATED));
    }

    @Test
    void getMe_requestUserIdMismatch_throwsPermissionDenied() {
        CapturingObserver<GetMeResponse> observer = new CapturingObserver<>();

        assertThatThrownBy(() ->
                runWithActor(ACTOR, () ->
                        service.getMe(GetMeRequest.newBuilder().setUserId("other-user").build(), observer)))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.PERMISSION_DENIED));
    }

    // ─── updateProfile ───────────────────────────────────────────────────────

    @Test
    void updateProfile_delegatesToIdempotencyExecutor() {
        UpdateProfileResponse stubResponse = UpdateProfileResponse.newBuilder().build();
        when(idempotencyExecutor.execute(any(), any(), any())).thenReturn(stubResponse);
        CapturingObserver<UpdateProfileResponse> observer = new CapturingObserver<>();

        runWithActor(ACTOR, () ->
                service.updateProfile(
                        UpdateProfileRequest.newBuilder().setUserId(ACTOR).setNickname("new").build(),
                        observer));

        verify(idempotencyExecutor).execute(any(), eq(UpdateProfileResponse.parser()), any());
        assertThat(observer.completed).isTrue();
        assertThat(observer.value).isEqualTo(stubResponse);
    }

    @Test
    void updateProfile_blankNickname_mapsToNullInCommand() {
        // blankToNull: empty string → null → service sees null nickname
        UpdateProfileResponse stubResponse = UpdateProfileResponse.newBuilder().build();
        when(idempotencyExecutor.execute(any(), any(), any())).thenAnswer(inv -> {
            // actually run the command to verify command values
            var command = (java.util.function.Supplier<?>) inv.getArgument(2);
            when(userProfileService.updateProfile(eq(ACTOR), any(UpdateProfileCommand.class)))
                    .thenReturn(profile(ACTOR));
            command.get();
            return stubResponse;
        });

        CapturingObserver<UpdateProfileResponse> observer = new CapturingObserver<>();
        runWithActor(ACTOR, () ->
                service.updateProfile(
                        UpdateProfileRequest.newBuilder().setUserId(ACTOR).setNickname("").build(),
                        observer));

        verify(userProfileService).updateProfile(eq(ACTOR),
                org.mockito.ArgumentMatchers.argThat(cmd -> cmd.nickname() == null));
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    static class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override public void onNext(T v) { value = v; }
        @Override public void onError(Throwable t) { error = t; }
        @Override public void onCompleted() { completed = true; }
    }
}

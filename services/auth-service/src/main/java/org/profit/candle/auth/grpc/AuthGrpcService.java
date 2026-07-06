package org.profit.candle.auth.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.admin.service.AdminLoginResult;
import org.profit.candle.auth.admin.service.AdminLoginService;
import org.profit.candle.auth.api.dto.ProviderResponse;
import org.profit.candle.auth.config.AuthProperties;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.identity.service.AuthMeService;
import org.profit.candle.auth.identity.service.AuthUserResult;
import org.profit.candle.auth.identity.service.OAuthProvidersService;
import org.profit.candle.proto.auth.v1.AdminLoginRequest;
import org.profit.candle.proto.auth.v1.AdminLoginResponse;
import org.profit.candle.proto.auth.v1.AuthUser;
import org.profit.candle.proto.auth.v1.AuthServiceGrpc;
import org.profit.candle.proto.auth.v1.GetMeRequest;
import org.profit.candle.proto.auth.v1.GetMeResponse;
import org.profit.candle.proto.auth.v1.ListProvidersRequest;
import org.profit.candle.proto.auth.v1.ListProvidersResponse;
import org.profit.candle.proto.auth.v1.Provider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final OAuthProvidersService oAuthProvidersService;
    private final AuthMeService authMeService;
    private final AdminLoginService adminLoginService;
    private final AuthProperties authProperties;

    @Override
    public void listProviders(ListProvidersRequest request, StreamObserver<ListProvidersResponse> observer) {
        ListProvidersResponse.Builder response = ListProvidersResponse.newBuilder();
        for (ProviderResponse p : oAuthProvidersService.listProviders().providers()) {
            response.addProviders(Provider.newBuilder()
                    .setName(p.name())
                    .setAuthorizationUrl(p.authorizationUrl())
                    .build());
        }
        observer.onNext(response.build());
        observer.onCompleted();
    }

    @Override
    public void getMe(GetMeRequest request, StreamObserver<GetMeResponse> observer) {
        try {
            AuthUserResult result = authMeService.getMe(request.getUserId());
            observer.onNext(GetMeResponse.newBuilder().setUser(toProto(result)).build());
            observer.onCompleted();
        } catch (AuthException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    @Override
    public void adminLogin(AdminLoginRequest request, StreamObserver<AdminLoginResponse> observer) {
        try {
            if (request.getUsername().isBlank() || request.getPassword().isBlank()) {
                throw new AuthException(AuthErrorCode.INVALID_ADMIN_REQUEST);
            }
            AdminLoginResult result = adminLoginService.login(request.getUsername(), request.getPassword());
            observer.onNext(AdminLoginResponse.newBuilder()
                    .setAccessToken(result.tokens().accessToken())
                    .setRefreshToken(result.tokens().refreshToken())
                    .setExpiresIn(result.tokens().expiresInSeconds())
                    .setRefreshExpiresIn(authProperties.jwt().refreshTokenTtl().toSeconds())
                    .setAdminId(result.adminId().toString())
                    .setUsername(result.username())
                    .setDisplayName(result.displayName())
                    .setRole(result.role().name())
                    .build());
            observer.onCompleted();
        } catch (AuthException e) {
            observer.onError(toGrpcStatus(e).asRuntimeException());
        }
    }

    private AuthUser toProto(AuthUserResult result) {
        return AuthUser.newBuilder()
                .setUserId(result.userId())
                .setEmail(result.email())
                .setProvider(result.provider())
                .setProviderSubject(result.providerSubject())
                .build();
    }

    private Status toGrpcStatus(AuthException e) {
        AuthErrorCode errorCode = e.errorCode();
        String code = errorCode.code();
        Status.Code grpcCode = switch (errorCode) {
            case AUTH_USER_NOT_FOUND, ADMIN_ACCOUNT_NOT_FOUND -> Status.Code.NOT_FOUND;
            case INVALID_AUTH_USER_ID, INVALID_ADMIN_REQUEST -> Status.Code.INVALID_ARGUMENT;
            case INVALID_ADMIN_CREDENTIALS -> Status.Code.UNAUTHENTICATED;
            case ADMIN_ACCOUNT_DISABLED, ADMIN_FORBIDDEN -> Status.Code.PERMISSION_DENIED;
            case ADMIN_ACCOUNT_LOCKED -> Status.Code.FAILED_PRECONDITION;
            default -> Status.Code.UNKNOWN;
        };
        return Status.fromCode(grpcCode).withDescription(code);
    }
}

package org.profit.candle.auth.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.api.dto.ProviderResponse;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.identity.service.AuthMeService;
import org.profit.candle.auth.identity.service.AuthUserResult;
import org.profit.candle.auth.identity.service.OAuthProvidersService;
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

    private AuthUser toProto(AuthUserResult result) {
        return AuthUser.newBuilder()
                .setUserId(result.userId())
                .setEmail(result.email())
                .setProvider(result.provider())
                .setProviderSubject(result.providerSubject())
                .build();
    }

    private Status toGrpcStatus(AuthException e) {
        String code = e.errorCode().code();
        if (code.equals(AuthErrorCode.AUTH_USER_NOT_FOUND.code())) {
            return Status.NOT_FOUND.withDescription(code);
        }
        if (code.equals(AuthErrorCode.INVALID_AUTH_USER_ID.code())) {
            return Status.INVALID_ARGUMENT.withDescription(code);
        }
        return Status.UNKNOWN.withDescription(code);
    }
}

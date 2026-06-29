package org.profit.candle.auth.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.api.dto.ProviderResponse;
import org.profit.candle.auth.identity.service.OAuthProvidersService;
import org.profit.candle.proto.auth.v1.AuthServiceGrpc;
import org.profit.candle.proto.auth.v1.ListProvidersRequest;
import org.profit.candle.proto.auth.v1.ListProvidersResponse;
import org.profit.candle.proto.auth.v1.Provider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final OAuthProvidersService oAuthProvidersService;

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
}

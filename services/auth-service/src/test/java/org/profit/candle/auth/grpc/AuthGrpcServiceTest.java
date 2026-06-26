package org.profit.candle.auth.grpc;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.api.dto.ProviderResponse;
import org.profit.candle.auth.api.dto.ProvidersResponse;
import org.profit.candle.auth.identity.service.OAuthProvidersService;
import org.profit.candle.proto.auth.v1.ListProvidersRequest;
import org.profit.candle.proto.auth.v1.ListProvidersResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthGrpcServiceTest {

    @Mock OAuthProvidersService oAuthProvidersService;
    @InjectMocks AuthGrpcService authGrpcService;

    @Test
    @SuppressWarnings("unchecked")
    void listProviders_callsOnNextAndOnCompleted() {
        when(oAuthProvidersService.listProviders()).thenReturn(
                new ProvidersResponse(List.of(
                        new ProviderResponse("google", "https://accounts.google.com/?client_id=x")
                ))
        );

        StreamObserver<ListProvidersResponse> observer = mock(StreamObserver.class);
        authGrpcService.listProviders(ListProvidersRequest.getDefaultInstance(), observer);

        verify(observer).onNext(any(ListProvidersResponse.class));
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listProviders_mapsNameAndAuthorizationUrl() {
        String expectedUrl = "https://accounts.google.com/?client_id=x";
        when(oAuthProvidersService.listProviders()).thenReturn(
                new ProvidersResponse(List.of(new ProviderResponse("google", expectedUrl)))
        );

        StreamObserver<ListProvidersResponse> observer = mock(StreamObserver.class);
        authGrpcService.listProviders(ListProvidersRequest.getDefaultInstance(), observer);

        ArgumentCaptor<ListProvidersResponse> captor = ArgumentCaptor.forClass(ListProvidersResponse.class);
        verify(observer).onNext(captor.capture());

        ListProvidersResponse response = captor.getValue();
        assertThat(response.getProvidersList()).hasSize(1);
        assertThat(response.getProviders(0).getName()).isEqualTo("google");
        assertThat(response.getProviders(0).getAuthorizationUrl()).isEqualTo(expectedUrl);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listProviders_mapsMultipleProviders() {
        when(oAuthProvidersService.listProviders()).thenReturn(
                new ProvidersResponse(List.of(
                        new ProviderResponse("google", "url-google"),
                        new ProviderResponse("kakao", "url-kakao")
                ))
        );

        StreamObserver<ListProvidersResponse> observer = mock(StreamObserver.class);
        authGrpcService.listProviders(ListProvidersRequest.getDefaultInstance(), observer);

        ArgumentCaptor<ListProvidersResponse> captor = ArgumentCaptor.forClass(ListProvidersResponse.class);
        verify(observer).onNext(captor.capture());

        assertThat(captor.getValue().getProvidersList()).hasSize(2);
        assertThat(captor.getValue().getProviders(1).getName()).isEqualTo("kakao");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listProviders_emptyProviders_returnsEmptyList() {
        when(oAuthProvidersService.listProviders()).thenReturn(new ProvidersResponse(List.of()));

        StreamObserver<ListProvidersResponse> observer = mock(StreamObserver.class);
        authGrpcService.listProviders(ListProvidersRequest.getDefaultInstance(), observer);

        ArgumentCaptor<ListProvidersResponse> captor = ArgumentCaptor.forClass(ListProvidersResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getProvidersList()).isEmpty();
    }
}

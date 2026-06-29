package org.profit.candle.auth.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.profit.candle.auth.api.dto.ProviderResponse;
import org.profit.candle.auth.api.dto.ProvidersResponse;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.profit.candle.auth.identity.service.AuthMeService;
import org.profit.candle.auth.identity.service.AuthUserResult;
import org.profit.candle.auth.identity.service.OAuthProvidersService;
import org.profit.candle.proto.auth.v1.GetMeRequest;
import org.profit.candle.proto.auth.v1.GetMeResponse;
import org.profit.candle.proto.auth.v1.ListProvidersRequest;
import org.profit.candle.proto.auth.v1.ListProvidersResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthGrpcServiceTest {

    @Mock OAuthProvidersService oAuthProvidersService;
    @Mock AuthMeService authMeService;
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

    @Test
    @SuppressWarnings("unchecked")
    void getMe_mapsUserFields() {
        String userId = UUID.randomUUID().toString();
        when(authMeService.getMe(userId)).thenReturn(
                new AuthUserResult(userId, "user@example.com", "google", "sub-1")
        );

        StreamObserver<GetMeResponse> observer = mock(StreamObserver.class);
        authGrpcService.getMe(GetMeRequest.newBuilder().setUserId(userId).build(), observer);

        ArgumentCaptor<GetMeResponse> captor = ArgumentCaptor.forClass(GetMeResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());

        assertThat(captor.getValue().getUser().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getUser().getEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getUser().getProvider()).isEqualTo("google");
        assertThat(captor.getValue().getUser().getProviderSubject()).isEqualTo("sub-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMe_notFound_returnsNotFound() {
        String userId = UUID.randomUUID().toString();
        when(authMeService.getMe(userId)).thenThrow(new AuthException(AuthErrorCode.AUTH_USER_NOT_FOUND));

        StreamObserver<GetMeResponse> observer = mock(StreamObserver.class);
        authGrpcService.getMe(GetMeRequest.newBuilder().setUserId(userId).build(), observer);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(captor.capture());
        assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMe_invalidUserId_returnsInvalidArgument() {
        when(authMeService.getMe("bad")).thenThrow(new AuthException(AuthErrorCode.INVALID_AUTH_USER_ID));

        StreamObserver<GetMeResponse> observer = mock(StreamObserver.class);
        authGrpcService.getMe(GetMeRequest.newBuilder().setUserId("bad").build(), observer);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(captor.getValue()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}

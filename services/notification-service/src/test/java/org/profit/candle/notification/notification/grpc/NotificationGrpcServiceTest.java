package org.profit.candle.notification.notification.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.profit.candle.notification.device.service.DeviceTokenService;
import org.profit.candle.notification.idempotency.service.IdempotencyExecutor;
import org.profit.candle.notification.notification.exception.NotificationErrorCode;
import org.profit.candle.notification.notification.service.NotificationService;
import org.profit.candle.proto.common.v1.CommandMetadata;
import org.profit.candle.proto.notification.v1.DevicePlatform;
import org.profit.candle.proto.notification.v1.RegisterDeviceTokenRequest;
import org.profit.candle.proto.notification.v1.RegisterDeviceTokenResponse;

class NotificationGrpcServiceTest {

    @Test
    void shouldRejectInvalidIdempotencyKeyFormat() {
        NotificationGrpcService service = service();
        CapturingObserver<RegisterDeviceTokenResponse> observer = new CapturingObserver<>();

        service.registerDeviceToken(RegisterDeviceTokenRequest.newBuilder()
                .setUserId("00000000-0000-0000-0000-000000000001")
                .setFcmToken("fcm-token")
                .setPlatform(DevicePlatform.DEVICE_PLATFORM_ANDROID)
                .setCommandMetadata(CommandMetadata.newBuilder()
                        .setIdempotencyKey("bad")
                        .build())
                .build(), observer);

        StatusRuntimeException error = (StatusRuntimeException) observer.error;
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(error.getStatus().getDescription())
                .isEqualTo(NotificationErrorCode.INVALID_IDEMPOTENCY_KEY.code());
    }

    @Test
    void shouldRejectMissingIdempotencyKey() {
        NotificationGrpcService service = service();
        CapturingObserver<RegisterDeviceTokenResponse> observer = new CapturingObserver<>();

        service.registerDeviceToken(RegisterDeviceTokenRequest.newBuilder()
                .setUserId("00000000-0000-0000-0000-000000000001")
                .setFcmToken("fcm-token")
                .setPlatform(DevicePlatform.DEVICE_PLATFORM_ANDROID)
                .build(), observer);

        StatusRuntimeException error = (StatusRuntimeException) observer.error;
        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(error.getStatus().getDescription())
                .isEqualTo(NotificationErrorCode.IDEMPOTENCY_KEY_REQUIRED.code());
    }

    private NotificationGrpcService service() {
        return new NotificationGrpcService(
                mock(NotificationService.class),
                mock(DeviceTokenService.class),
                mock(IdempotencyExecutor.class)
        );
    }

    private static final class CapturingObserver<T> implements StreamObserver<T> {

        private Throwable error;

        @Override
        public void onNext(T value) {
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onCompleted() {
        }
    }
}

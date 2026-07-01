package org.profit.candle.notification.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.profit.candle.notification.device.dto.DeviceTokenResult;
import org.profit.candle.notification.device.dto.RegisterDeviceTokenCommand;
import org.profit.candle.notification.device.entity.DevicePlatform;
import org.profit.candle.notification.device.entity.DeviceToken;
import org.profit.candle.notification.device.repository.DeviceTokenReader;
import org.profit.candle.notification.device.repository.DeviceTokenWriter;
import org.profit.candle.notification.outbox.entity.OutboxEvent;
import org.profit.candle.notification.outbox.repository.OutboxEventWriter;
import org.profit.candle.notification.outbox.service.OutboxEventService;

class DefaultDeviceTokenServiceTest {

    @Test
    void shouldRegisterNewDeviceToken() {
        FakeDeviceTokenRepository deviceTokenRepository = new FakeDeviceTokenRepository();
        FakeOutboxEventWriter outboxEventWriter = new FakeOutboxEventWriter();
        DefaultDeviceTokenService service = service(deviceTokenRepository, outboxEventWriter);
        UUID userId = UUID.randomUUID();

        DeviceTokenResult result = service.register(new RegisterDeviceTokenCommand(
                userId,
                "fcm-token",
                DevicePlatform.ANDROID,
                "device-1",
                "idem-key-1"
        ));

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.fcmToken()).isEqualTo("fcm-token");
        assertThat(result.platform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(result.deviceId()).isEqualTo("device-1");
        assertThat(result.active()).isTrue();
        assertThat(deviceTokenRepository.saved).hasSize(1);
        assertThat(outboxEventWriter.saved)
                .extracting(OutboxEvent::getEventType)
                .containsExactly("DeviceTokenRegistered");
    }

    @Test
    void shouldReactivateExistingTokenAndUpdatePlatformAndDeviceId() {
        FakeDeviceTokenRepository deviceTokenRepository = new FakeDeviceTokenRepository();
        FakeOutboxEventWriter outboxEventWriter = new FakeOutboxEventWriter();
        UUID originalUserId = UUID.randomUUID();
        DeviceToken existing = DeviceToken.register(
                originalUserId,
                "fcm-token",
                DevicePlatform.WEB,
                "old-device"
        );
        existing.deactivate();
        deviceTokenRepository.saved.add(existing);
        DefaultDeviceTokenService service = service(deviceTokenRepository, outboxEventWriter);
        UUID newUserId = UUID.randomUUID();

        DeviceTokenResult result = service.register(new RegisterDeviceTokenCommand(
                newUserId,
                "fcm-token",
                DevicePlatform.IOS,
                "new-device",
                "idem-key-2"
        ));

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.userId()).isEqualTo(newUserId);
        assertThat(result.platform()).isEqualTo(DevicePlatform.IOS);
        assertThat(result.deviceId()).isEqualTo("new-device");
        assertThat(result.active()).isTrue();
        assertThat(deviceTokenRepository.saved).hasSize(1);
        assertThat(outboxEventWriter.saved)
                .extracting(OutboxEvent::getEventType)
                .containsExactly("DeviceTokenRegistered");
    }

    private DefaultDeviceTokenService service(
            FakeDeviceTokenRepository deviceTokenRepository,
            FakeOutboxEventWriter outboxEventWriter
    ) {
        return new DefaultDeviceTokenService(
                deviceTokenRepository,
                deviceTokenRepository,
                new OutboxEventService(outboxEventWriter, new ObjectMapper())
        );
    }

    private static final class FakeDeviceTokenRepository
            implements DeviceTokenReader, DeviceTokenWriter {

        private final List<DeviceToken> saved = new ArrayList<>();

        @Override
        public Optional<DeviceToken> findByFcmToken(String fcmToken) {
            return saved.stream()
                    .filter(token -> token.getFcmToken().equals(fcmToken))
                    .findFirst();
        }

        @Override
        public List<DeviceToken> listActiveByUserId(UUID userId) {
            return saved.stream()
                    .filter(token -> token.getUserId().equals(userId))
                    .filter(DeviceToken::isActive)
                    .toList();
        }

        @Override
        public DeviceToken save(DeviceToken deviceToken) {
            findByFcmToken(deviceToken.getFcmToken()).ifPresent(saved::remove);
            saved.add(deviceToken);
            return deviceToken;
        }
    }

    private static final class FakeOutboxEventWriter implements OutboxEventWriter {

        private final List<OutboxEvent> saved = new ArrayList<>();

        @Override
        public OutboxEvent save(OutboxEvent event) {
            saved.add(event);
            return event;
        }
    }
}

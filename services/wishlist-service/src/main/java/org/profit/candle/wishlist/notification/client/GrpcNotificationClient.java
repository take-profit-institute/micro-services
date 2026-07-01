package org.profit.candle.wishlist.notification.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.profit.candle.proto.common.v1.CommandMetadata;
import org.profit.candle.proto.notification.v1.CreateNotificationRequest;
import org.profit.candle.proto.notification.v1.CreateNotificationResponse;
import org.profit.candle.proto.notification.v1.NotificationServiceGrpc;
import org.profit.candle.proto.notification.v1.NotificationType;
import org.profit.candle.wishlist.alert.entity.AlertDirection;
import org.profit.candle.wishlist.config.WishlistProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GrpcNotificationClient implements NotificationClient {
    private final ObjectMapper objectMapper;
    private final WishlistProperties properties;
    private final ManagedChannel channel;
    private final NotificationServiceGrpc.NotificationServiceBlockingStub stub;

    public GrpcNotificationClient(ObjectMapper objectMapper, WishlistProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.channel = ManagedChannelBuilder.forTarget(properties.notification().grpcAddress())
                .usePlaintext()
                .build();
        this.stub = NotificationServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public UUID createPriceAlertNotification(PriceAlertNotificationCommand command) {
        CreateNotificationRequest request = CreateNotificationRequest.newBuilder()
                .setUserId(command.userId().toString())
                .setType(toProtoType(command.direction()))
                .setTitle(title(command.direction()))
                .setBody(body(command))
                .setMetaJson(metaJson(command))
                .setCommandMetadata(CommandMetadata.newBuilder()
                        .setIdempotencyKey(command.idempotencyKey())
                        .build())
                .build();

        CreateNotificationResponse response = stub
                .withDeadlineAfter(
                        properties.notification().deadline().toMillis(),
                        java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .createNotification(request);
        return UUID.fromString(response.getNotification().getId());
    }

    @PreDestroy
    void shutdown() {
        channel.shutdown();
    }

    private static NotificationType toProtoType(AlertDirection direction) {
        return switch (direction) {
            case RISE -> NotificationType.NOTIFICATION_TYPE_PRICE_RISE;
            case FALL -> NotificationType.NOTIFICATION_TYPE_PRICE_FALL;
        };
    }

    private static String title(AlertDirection direction) {
        return direction == AlertDirection.RISE ? "관심종목 급등" : "관심종목 급락";
    }

    private static String body(PriceAlertNotificationCommand command) {
        BigDecimal percent = BigDecimal.valueOf(command.changeBasisPoints())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .abs();
        String verb = command.direction() == AlertDirection.RISE ? "상승" : "하락";
        return "%s이(가) 오늘 시가 대비 %s%% %s했습니다."
                .formatted(command.symbol(), percent.toPlainString(), verb);
    }

    private String metaJson(PriceAlertNotificationCommand command) {
        try {
            BigDecimal percent = BigDecimal.valueOf(command.changeBasisPoints())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return objectMapper.writeValueAsString(Map.of(
                    "source", "wishlist-service",
                    "symbol", command.symbol(),
                    "tradingDate", command.tradingDate().toString(),
                    "direction", command.direction().name(),
                    "openPrice", command.openPrice(),
                    "triggerPrice", command.triggerPrice(),
                    "changePercent", percent.toPlainString()
            ));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize wishlist price alert metadata", e);
            return "{}";
        }
    }
}

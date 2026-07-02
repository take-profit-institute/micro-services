package org.profit.candle.notification.notification.dto;

import java.util.List;

public record ListNotificationsResult(
        List<NotificationResult> notifications,
        String nextPageToken
) {
}

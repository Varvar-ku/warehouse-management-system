package ru.warehouse.notification.dto;

import ru.warehouse.notification.entity.Notification;
import ru.warehouse.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        UUID requestId,
        Instant createdAt,
        Instant readAt,
        boolean read) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getRequestId(), n.getCreatedAt(), n.getReadAt(), n.getReadAt() != null);
    }
}

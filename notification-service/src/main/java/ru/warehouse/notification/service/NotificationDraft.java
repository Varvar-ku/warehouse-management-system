package ru.warehouse.notification.service;

import ru.warehouse.notification.entity.NotificationType;

import java.util.UUID;

public record NotificationDraft(
        UUID recipientId,
        NotificationType type,
        String title,
        String message,
        UUID requestId) {
}

package ru.warehouse.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Событие аудита из топика wms.audit.v1. Неизвестные поля игнорируются: в этот топик пишут
 * и другие сервисы со своим содержимым details.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
        UUID eventId,
        String service,
        String action,
        String entityType,
        String entityId,
        String traceId,
        Details details) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Details(
            String requestNumber,
            UUID requesterId,
            String fromStatus,
            String newStatus,
            UUID actorId) {
    }
}

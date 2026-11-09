package ru.warehouse.audit.model;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditEventView(
        long id,
        UUID eventId,
        int schemaVersion,
        String service,
        String action,
        String entityType,
        String entityId,
        UUID actorId,
        String actorRole,
        Instant occurredAt,
        Instant receivedAt,
        String traceId,
        JsonNode details) {
}

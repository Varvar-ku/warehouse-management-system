package ru.warehouse.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Событие аудита из wms.audit.v1 (общий конверт AuditEvent). Нужные поля берутся из details. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditEnvelope(
        UUID eventId,
        String service,
        String action,
        String entityType,
        String entityId,
        String actorId,
        String traceId,
        Instant occurredAt,
        Map<String, Object> details) {
}

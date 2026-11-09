package ru.warehouse.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit event envelope, schema version 1.
 * <p>
 * Naming convention for {@code action}: {@code <entity>.<verb>} in lower case,
 * e.g. {@code request.submitted}, {@code issue.confirmed}, {@code writeoff.approved}.
 * {@code entityId} is a string so any key type fits.
 * {@code details} must be small and must not contain secrets or personal data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditEvent(
        UUID eventId,
        int schemaVersion,
        String service,
        String action,
        String entityType,
        String entityId,
        UUID actorId,
        String actorRole,
        Instant occurredAt,
        String traceId,
        Map<String, Object> details) {

    public static final int CURRENT_SCHEMA = 1;

    /** Creates an event with a fresh id and the current time. traceId is filled by the publisher. */
    public static AuditEvent of(String service, String action, String entityType, String entityId,
                                UUID actorId, String actorRole, Map<String, Object> details) {
        return new AuditEvent(UUID.randomUUID(), CURRENT_SCHEMA, service, action, entityType, entityId,
                actorId, actorRole, Instant.now(), null, details == null ? Map.of() : details);
    }

    public AuditEvent withTraceId(String newTraceId) {
        return new AuditEvent(eventId, schemaVersion, service, action, entityType, entityId,
                actorId, actorRole, occurredAt, newTraceId, details);
    }
}

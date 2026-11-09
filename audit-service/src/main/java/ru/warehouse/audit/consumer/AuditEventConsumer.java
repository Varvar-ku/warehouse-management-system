package ru.warehouse.audit.consumer;

import ru.warehouse.audit.service.AuditLogService;
import ru.warehouse.common.event.AuditEvent;
import ru.warehouse.common.event.EventTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final ObjectMapper mapper;
    private final AuditLogService service;

    public AuditEventConsumer(ObjectMapper mapper, AuditLogService service) {
        this.mapper = mapper;
        this.service = service;
    }

    /**
     * A malformed message throws and, after the error handler's decision, goes to
     * the dead-letter topic {@code wms.audit.v1.DLT}. Duplicates are dropped by event_id.
     */
    @KafkaListener(topics = EventTopics.AUDIT)
    public void onMessage(String payload) {
        AuditEvent event = mapper.readValue(payload, AuditEvent.class);
        validate(event);
        boolean stored = service.save(event);
        if (!stored) {
            log.debug("Duplicate audit event ignored: {}", event.eventId());
        }
    }

    private static void validate(AuditEvent e) {
        if (e.eventId() == null || e.occurredAt() == null
                || isBlank(e.service()) || isBlank(e.action())
                || isBlank(e.entityType()) || isBlank(e.entityId())) {
            throw new IllegalArgumentException("Invalid audit event: required field is missing");
        }
        if (e.schemaVersion() != AuditEvent.CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported audit schema version: " + e.schemaVersion());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

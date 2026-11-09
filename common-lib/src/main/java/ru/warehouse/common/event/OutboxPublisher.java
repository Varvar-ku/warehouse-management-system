package ru.warehouse.common.event;

import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.common.web.TraceIdFilter;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes events to the {@code outbox_event} table of the calling service.
 * Must be called inside the business transaction (propagation MANDATORY):
 * the event is committed or rolled back together with the business change.
 */
public class OutboxPublisher {

    /** Ключ MDC берётся у TraceIdFilter: свой литерал мог разойтись, и trace-id в событиях пропадал бы молча. */
    static final String TRACE_MDC_KEY = TraceIdFilter.MDC_KEY;

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public OutboxPublisher(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishAudit(AuditEvent event) {
        AuditEvent e = event.traceId() == null ? event.withTraceId(MDC.get(TRACE_MDC_KEY)) : event;
        jdbc.sql("""
                        INSERT INTO outbox_event (event_id, topic, event_key, payload)
                        VALUES (:eventId, :topic, :key, CAST(:payload AS jsonb))
                        """)
                .param("eventId", e.eventId())
                .param("topic", EventTopics.AUDIT)
                .param("key", e.entityType() + ":" + e.entityId())
                .param("payload", mapper.writeValueAsString(e))
                .update();
    }
    /** Короткая форма: роль не передаётся, entityId приводится к строке. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void audit(String service, String action, String entityType, Object entityId,
                      UUID actorId, Map<String, Object> details) {
        publishAudit(AuditEvent.of(service, action, entityType, String.valueOf(entityId),
                actorId, null, details));
    }

    /** Снимок движения остатка для report-service. Ключ: партия (порядок событий по партии). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishStockMovement(StockMovementEvent event) {
        StockMovementEvent e = event.traceId() == null ? event.withTraceId(MDC.get(TRACE_MDC_KEY)) : event;
        String key = e.batchId() != null ? e.batchId().toString() : e.productId().toString();
        jdbc.sql("""
                INSERT INTO outbox_event (event_id, topic, event_key, payload)
                VALUES (:eventId, :topic, :key, CAST(:payload AS jsonb))
                """)
                .param("eventId", e.eventId())
                .param("topic", EventTopics.STOCK_MOVEMENTS)
                .param("key", key)
                .param("payload", mapper.writeValueAsString(e))
                .update();
    }

    /** Смена статуса секции. Ключ: секция (порядок событий по одной секции сохраняется). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSectionStatus(SectionStatusEvent e) {
        jdbc.sql("""
                INSERT INTO outbox_event (event_id, topic, event_key, payload)
                VALUES (:eventId, :topic, :key, CAST(:payload AS jsonb))
                """)
                .param("eventId", e.eventId())
                .param("topic", SectionStatusEvent.TOPIC)
                .param("key", e.sectionId().toString())
                .param("payload", mapper.writeValueAsString(e))
                .update();
    }

    /** details("number", n, "reason", r): пары ключ-значение; пустые значения пропускаются. */
    public static Map<String, Object> details(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("details() expects key-value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }
}

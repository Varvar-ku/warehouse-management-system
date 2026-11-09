package ru.warehouse.audit.service;

import ru.warehouse.audit.model.AuditEventView;
import ru.warehouse.audit.model.AuditPage;
import ru.warehouse.common.event.AuditEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final int MAX_PAGE_SIZE = 200;

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public AuditLogService(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** @return true if stored, false if an event with the same id already exists. */
    @Transactional
    public boolean save(AuditEvent e) {
        String details = mapper.writeValueAsString(e.details() == null ? Map.of() : e.details());
        int rows = jdbc.sql("""
                        INSERT INTO audit_log (event_id, schema_version, source_service, action, entity_type,
                                               entity_id, actor_id, actor_role, occurred_at, trace_id, details)
                        VALUES (:eventId, :schema, :service, :action, :entityType,
                                :entityId, CAST(:actorId AS uuid), :actorRole, :occurredAt, :traceId,
                                CAST(:details AS jsonb))
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", e.eventId())
                .param("schema", e.schemaVersion())
                .param("service", e.service())
                .param("action", e.action())
                .param("entityType", e.entityType())
                .param("entityId", e.entityId())
                .param("actorId", e.actorId() == null ? null : e.actorId().toString())
                .param("actorRole", e.actorRole())
                .param("occurredAt", OffsetDateTime.ofInstant(e.occurredAt(), ZoneOffset.UTC))
                .param("traceId", e.traceId())
                .param("details", details)
                .update();
        return rows > 0;
    }

    @Transactional(readOnly = true)
    public AuditPage search(String entityType, String entityId, UUID actorId, String action,
                            Instant from, Instant to, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> p = new HashMap<>();
        if (entityType != null && !entityType.isBlank()) {
            where.append(" AND entity_type = :entityType");
            p.put("entityType", entityType);
        }
        if (entityId != null && !entityId.isBlank()) {
            where.append(" AND entity_id = :entityId");
            p.put("entityId", entityId);
        }
        if (actorId != null) {
            where.append(" AND actor_id = CAST(:actorId AS uuid)");
            p.put("actorId", actorId.toString());
        }
        if (action != null && !action.isBlank()) {
            if (action.endsWith("*")) {
                where.append(" AND action LIKE :action");
                p.put("action", action.substring(0, action.length() - 1) + "%");
            } else {
                where.append(" AND action = :action");
                p.put("action", action);
            }
        }
        if (from != null) {
            where.append(" AND occurred_at >= :from");
            p.put("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC));
        }
        if (to != null) {
            where.append(" AND occurred_at < :to");
            p.put("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
        }

        long total = jdbc.sql("SELECT count(*) FROM audit_log" + where)
                .params(p)
                .query(Long.class)
                .single();

        Map<String, Object> dataParams = new HashMap<>(p);
        dataParams.put("limit", safeSize);
        dataParams.put("offset", (long) safePage * safeSize);

        List<AuditEventView> items = jdbc.sql("""
                        SELECT id, event_id, schema_version, source_service, action, entity_type, entity_id,
                               actor_id, actor_role, occurred_at, received_at, trace_id, details::text AS details
                        FROM audit_log
                        """ + where + """
                         ORDER BY occurred_at DESC, id DESC
                         LIMIT :limit OFFSET :offset
                        """)
                .params(dataParams)
                .query((rs, i) -> new AuditEventView(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getInt("schema_version"),
                        rs.getString("source_service"),
                        rs.getString("action"),
                        rs.getString("entity_type"),
                        rs.getString("entity_id"),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("actor_role"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("received_at", OffsetDateTime.class).toInstant(),
                        rs.getString("trace_id"),
                        mapper.readTree(rs.getString("details"))))
                .list();

        int totalPages = (int) ((total + safeSize - 1) / safeSize);
        return new AuditPage(items, safePage, safeSize, total, totalPages);
    }
}

package ru.warehouse.report.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.warehouse.report.dto.PickingDtos.PickingEventRow;
import ru.warehouse.report.dto.PickingDtos.WorkerPerformanceRow;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PickingEventRepository {

    /** to: правая граница НЕ включается. */
    public record Filter(Instant from, Instant to, UUID workerId, String taskNumber, String action) {
    }

    public record Slice<T>(List<T> items, long total) {
    }

    private static final String EVENT_COLUMNS = """
            occurred_at, action, task_number, actor_id, worker_id,
            details->>'replaceWith' AS replace_with,
            details->>'requestNumber' AS request_number,
            NULLIF(details->>'lineNo', '')::int AS line_no,
            details->>'sectionCode' AS section_code,
            details->>'batchNumber' AS batch_number,
            (COALESCE(details->>'quantity', details->>'assignedQuantity', details->>'movedQuantity'))::numeric AS quantity,
            (details->>'pickedQuantity')::numeric AS picked_quantity,
            (details->>'shortage')::boolean AS shortage,
            COALESCE(details->>'comment', details->>'reason') AS note""";

    private final JdbcClient jdbc;

    public PickingEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 0 значит, что событие уже было (повторная доставка). */
    public int insert(UUID id, Instant occurredAt, String action, UUID taskId, String taskNumber,
                      UUID actorId, UUID workerId, String traceId, String detailsJson) {
        return jdbc.sql("""
                        INSERT INTO picking_events (id, occurred_at, action, task_id, task_number,
                                                    actor_id, worker_id, trace_id, details)
                        VALUES (:id, :at, :action, :taskId, :taskNumber, :actorId, :workerId, :traceId,
                                CAST(:details AS jsonb))
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", id)
                .param("at", utc(occurredAt))
                .param("action", action)
                .param("taskId", taskId)
                .param("taskNumber", taskNumber)
                .param("actorId", actorId)
                .param("workerId", workerId)
                .param("traceId", traceId)
                .param("details", detailsJson)
                .update();
    }

    public Slice<PickingEventRow> findEvents(Filter f, int page, int size, RowMapper<PickingEventRow> mapper) {
        Map<String, Object> p = new HashMap<>();
        String where = where(f, p, false);
        long total = jdbc.sql("SELECT count(*) FROM picking_events" + where).params(p).query(Long.class).single();
        Map<String, Object> pageParams = new HashMap<>(p);
        pageParams.put("limit", size);
        pageParams.put("offset", (long) page * size);
        List<PickingEventRow> items = jdbc.sql("SELECT " + EVENT_COLUMNS + " FROM picking_events" + where
                        + " ORDER BY occurred_at DESC, id LIMIT :limit OFFSET :offset")
                .params(pageParams).query(mapper).list();
        return new Slice<>(items, total);
    }

    /** Сводка по исполнителям: собранное по item_picked и снятия по worker_removed за период. */
    public List<WorkerPerformanceRow> workerPerformance(Filter f) {
        Map<String, Object> p = new HashMap<>();
        String where = where(f, p, true);
        String sql = """
                WITH picks AS (
                    SELECT worker_id,
                           count(*) AS rows_processed,
                           count(DISTINCT task_id) AS tasks,
                           COALESCE(sum((details->>'assignedQuantity')::numeric), 0) AS assigned_qty,
                           COALESCE(sum((details->>'pickedQuantity')::numeric), 0) AS picked_qty,
                           count(*) FILTER (WHERE (details->>'shortage')::boolean) AS shortage_rows,
                           min(occurred_at) AS first_pick_at,
                           max(occurred_at) AS last_pick_at
                    FROM picking_events""" + where + """
                      AND action = 'picking.item_picked'
                    GROUP BY worker_id
                ), removals AS (
                    SELECT worker_id,
                           count(*) AS removals,
                           COALESCE(sum((details->>'movedQuantity')::numeric), 0) AS moved_qty
                    FROM picking_events""" + where + """
                      AND action = 'picking.worker_removed'
                    GROUP BY worker_id
                )
                SELECT COALESCE(p.worker_id, r.worker_id) AS worker_id,
                       COALESCE(p.tasks, 0) AS tasks,
                       COALESCE(p.rows_processed, 0) AS rows_processed,
                       COALESCE(p.assigned_qty, 0) AS assigned_qty,
                       COALESCE(p.picked_qty, 0) AS picked_qty,
                       COALESCE(p.shortage_rows, 0) AS shortage_rows,
                       p.first_pick_at, p.last_pick_at,
                       COALESCE(r.removals, 0) AS removals,
                       COALESCE(r.moved_qty, 0) AS moved_qty
                FROM picks p FULL OUTER JOIN removals r ON p.worker_id = r.worker_id
                ORDER BY picked_qty DESC, worker_id
                """;
        return jdbc.sql(sql).params(p).query((rs, i) -> new WorkerPerformanceRow(
                rs.getObject("worker_id", UUID.class), null,
                rs.getLong("tasks"), rs.getLong("rows_processed"),
                rs.getBigDecimal("assigned_qty"), rs.getBigDecimal("picked_qty"), rs.getLong("shortage_rows"),
                rs.getObject("first_pick_at", OffsetDateTime.class),
                rs.getObject("last_pick_at", OffsetDateTime.class),
                rs.getLong("removals"), rs.getBigDecimal("moved_qty"))).list();
    }

    /**
     * Общее условие. forWorkerTotals: фильтр исполнителя только по worker_id (для сводки),
     * иначе исполнитель ищется и среди выполнивших операцию (actor_id).
     */
    private static String where(Filter f, Map<String, Object> p, boolean forWorkerTotals) {
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (f.from() != null) {
            w.append(" AND occurred_at >= :from");
            p.put("from", utc(f.from()));
        }
        if (f.to() != null) {
            w.append(" AND occurred_at < :to");
            p.put("to", utc(f.to()));
        }
        if (f.workerId() != null) {
            w.append(forWorkerTotals ? " AND worker_id = :workerId"
                    : " AND (worker_id = :workerId OR actor_id = :workerId)");
            p.put("workerId", f.workerId());
        }
        if (f.taskNumber() != null && !f.taskNumber().isBlank()) {
            w.append(" AND task_number = :taskNumber");
            p.put("taskNumber", f.taskNumber().trim());
        }
        if (f.action() != null && !f.action().isBlank()) {
            w.append(" AND action = :action");
            p.put("action", f.action().trim());
        }
        return w.toString();
    }

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}

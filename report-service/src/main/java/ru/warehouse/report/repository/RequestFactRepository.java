package ru.warehouse.report.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.warehouse.report.dto.RequestReportDtos.RequestRow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class RequestFactRepository {

    /** to: правая граница НЕ включается; период отбирает по дате создания заявки. */
    public record Filter(Instant from, Instant to, String status, UUID requesterId, String number, UUID warehouseId) {
    }

    public record Slice<T>(List<T> items, long total) {
    }

    private static final String COLUMNS = """
            number, requester_id, warehouse_id, status, items_count,
            requested_quantity, approved_quantity, reserved_quantity, issued_quantity,
            created_at, closed_at, issue_number""";

    private final JdbcClient jdbc;

    public RequestFactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Обновляет снимок заявки. Старое событие (раньше уже применённого) игнорируется; пустые поля не затирают
     * последние известные значения. closed_at ставится по событию перехода в CLOSED.
     */
    public int upsert(UUID requestId, String number, UUID requesterId, UUID warehouseId, String status,
                      Integer itemsCount, BigDecimal requested, BigDecimal approved, BigDecimal reserved,
                      BigDecimal issued, Instant createdAt, String issueNumber, Instant eventAt, UUID eventId) {
        return jdbc.sql("""
                        INSERT INTO request_facts (request_id, number, requester_id, warehouse_id, status, items_count,
                            requested_quantity, approved_quantity, reserved_quantity, issued_quantity,
                            created_at, closed_at, issue_number, last_event_at, last_event_id)
                        VALUES (:id, :number, :requester, :warehouse, :status, :items,
                            :requested, :approved, :reserved, :issued,
                            COALESCE(CAST(:createdAt AS timestamptz), CAST(:at AS timestamptz)),
                            CASE WHEN :status = 'CLOSED' THEN CAST(:at AS timestamptz) END,
                            :issueNumber, CAST(:at AS timestamptz), :eventId)
                        ON CONFLICT (request_id) DO UPDATE SET
                            number = EXCLUDED.number,
                            requester_id = COALESCE(EXCLUDED.requester_id, request_facts.requester_id),
                            warehouse_id = COALESCE(EXCLUDED.warehouse_id, request_facts.warehouse_id),
                            status = EXCLUDED.status,
                            items_count = COALESCE(EXCLUDED.items_count, request_facts.items_count),
                            requested_quantity = COALESCE(EXCLUDED.requested_quantity, request_facts.requested_quantity),
                            approved_quantity = COALESCE(EXCLUDED.approved_quantity, request_facts.approved_quantity),
                            reserved_quantity = COALESCE(EXCLUDED.reserved_quantity, request_facts.reserved_quantity),
                            issued_quantity = COALESCE(EXCLUDED.issued_quantity, request_facts.issued_quantity),
                            created_at = COALESCE(CAST(:createdAt AS timestamptz), request_facts.created_at),
                            closed_at = CASE WHEN EXCLUDED.status = 'CLOSED' THEN EXCLUDED.last_event_at
                                             ELSE request_facts.closed_at END,
                            issue_number = COALESCE(EXCLUDED.issue_number, request_facts.issue_number),
                            last_event_at = EXCLUDED.last_event_at,
                            last_event_id = EXCLUDED.last_event_id
                        WHERE request_facts.last_event_at <= EXCLUDED.last_event_at
                        """)
                .param("id", requestId)
                .param("number", number)
                .param("requester", requesterId)
                .param("warehouse", warehouseId)
                .param("status", status)
                .param("items", itemsCount)
                .param("requested", requested)
                .param("approved", approved)
                .param("reserved", reserved)
                .param("issued", issued)
                .param("createdAt", utc(createdAt))
                .param("issueNumber", issueNumber)
                .param("at", utc(eventAt))
                .param("eventId", eventId)
                .update();
    }

    public Slice<RequestRow> find(Filter f, int page, int size, RowMapper<RequestRow> mapper) {
        Map<String, Object> p = new HashMap<>();
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (f.from() != null) {
            w.append(" AND created_at >= :from");
            p.put("from", utc(f.from()));
        }
        if (f.to() != null) {
            w.append(" AND created_at < :to");
            p.put("to", utc(f.to()));
        }
        if (f.status() != null) {
            w.append(" AND status = :status");
            p.put("status", f.status());
        }
        if (f.requesterId() != null) {
            w.append(" AND requester_id = :requesterId");
            p.put("requesterId", f.requesterId());
        }
        if (f.number() != null && !f.number().isBlank()) {
            w.append(" AND lower(number) LIKE lower(:number)");
            p.put("number", "%" + f.number().trim() + "%");
        }
        if (f.warehouseId() != null) {
            w.append(" AND warehouse_id = :warehouseId");
            p.put("warehouseId", f.warehouseId());
        }
        long total = jdbc.sql("SELECT count(*) FROM request_facts" + w).params(p).query(Long.class).single();
        Map<String, Object> pp = new HashMap<>(p);
        pp.put("limit", size);
        pp.put("offset", (long) page * size);
        List<RequestRow> items = jdbc.sql("SELECT " + COLUMNS + " FROM request_facts" + w
                        + " ORDER BY created_at DESC, request_id LIMIT :limit OFFSET :offset")
                .params(pp).query(mapper).list();
        return new Slice<>(items, total);
    }

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}

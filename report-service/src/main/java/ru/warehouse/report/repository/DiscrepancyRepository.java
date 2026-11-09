package ru.warehouse.report.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.warehouse.report.dto.RequestReportDtos.DiscrepancyRow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DiscrepancyRepository {

    /** to: правая граница НЕ включается. */
    public record Filter(Instant from, Instant to, String source, String documentNumber, String sku,
                         UUID responsibleId, boolean unresolvedOnly) {
    }

    public record Slice<T>(List<T> items, long total) {
    }

    private static final String COLUMNS = """
            occurred_at, source, document_number, line_no, product_sku, product_name, unit_code,
            required_quantity, actual_quantity, deviation, reason, responsible_id,
            planned_section, actual_section, issue_number,
            resolution, resolution_comment, resolved_at, resolved_by""";

    private final JdbcClient jdbc;

    public DiscrepancyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 0 значит, что событие уже было (повторная доставка). */
    public int insert(UUID id, Instant occurredAt, String source, UUID documentId, String documentNumber,
                      Integer lineNo, String sku, String name, String unit, BigDecimal required, BigDecimal actual,
                      String reason, UUID responsibleId, String plannedSection, String actualSection,
                      String issueNumber) {
        return jdbc.sql("""
                        INSERT INTO discrepancy_facts (id, occurred_at, source, document_id, document_number, line_no,
                            product_sku, product_name, unit_code, required_quantity, actual_quantity, reason,
                            responsible_id, planned_section, actual_section, issue_number)
                        VALUES (:id, :at, :source, :documentId, :documentNumber, :lineNo,
                            :sku, :name, :unit, :required, :actual, :reason,
                            :responsible, :plannedSection, :actualSection, :issueNumber)
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", id)
                .param("at", utc(occurredAt))
                .param("source", source)
                .param("documentId", documentId)
                .param("documentNumber", documentNumber)
                .param("lineNo", lineNo)
                .param("sku", sku)
                .param("name", name)
                .param("unit", unit)
                .param("required", required)
                .param("actual", actual)
                .param("reason", reason)
                .param("responsible", responsibleId)
                .param("plannedSection", plannedSection)
                .param("actualSection", actualSection)
                .param("issueNumber", issueNumber)
                .update();
    }

    /** Закрывает последнее нерешённое расхождение приёмки по позиции документа. */
    public int resolveReceipt(UUID documentId, Integer lineNo, String decision, String comment,
                              Instant at, UUID by) {
        return jdbc.sql("""
                        UPDATE discrepancy_facts
                        SET resolution = :decision, resolution_comment = :comment, resolved_at = :at, resolved_by = :by
                        WHERE id = (SELECT id FROM discrepancy_facts
                                    WHERE source = 'RECEIPT' AND document_id = :documentId AND line_no = :lineNo
                                      AND resolution IS NULL
                                    ORDER BY occurred_at DESC LIMIT 1)
                        """)
                .param("decision", decision)
                .param("comment", comment)
                .param("at", utc(at))
                .param("by", by)
                .param("documentId", documentId)
                .param("lineNo", lineNo)
                .update();
    }

    public Slice<DiscrepancyRow> find(Filter f, int page, int size, RowMapper<DiscrepancyRow> mapper) {
        Map<String, Object> p = new HashMap<>();
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        if (f.from() != null) {
            w.append(" AND occurred_at >= :from");
            p.put("from", utc(f.from()));
        }
        if (f.to() != null) {
            w.append(" AND occurred_at < :to");
            p.put("to", utc(f.to()));
        }
        if (f.source() != null) {
            w.append(" AND source = :source");
            p.put("source", f.source());
        }
        if (f.documentNumber() != null && !f.documentNumber().isBlank()) {
            w.append(" AND document_number = :documentNumber");
            p.put("documentNumber", f.documentNumber().trim());
        }
        if (f.sku() != null && !f.sku().isBlank()) {
            w.append(" AND lower(product_sku) LIKE lower(:sku)");
            p.put("sku", "%" + f.sku().trim() + "%");
        }
        if (f.responsibleId() != null) {
            w.append(" AND responsible_id = :responsibleId");
            p.put("responsibleId", f.responsibleId());
        }
        if (f.unresolvedOnly()) {
            w.append(" AND source = 'RECEIPT' AND resolution IS NULL");
        }
        long total = jdbc.sql("SELECT count(*) FROM discrepancy_facts" + w).params(p).query(Long.class).single();
        Map<String, Object> pp = new HashMap<>(p);
        pp.put("limit", size);
        pp.put("offset", (long) page * size);
        List<DiscrepancyRow> items = jdbc.sql("SELECT " + COLUMNS + " FROM discrepancy_facts" + w
                        + " ORDER BY occurred_at DESC, id LIMIT :limit OFFSET :offset")
                .params(pp).query(mapper).list();
        return new Slice<>(items, total);
    }

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}

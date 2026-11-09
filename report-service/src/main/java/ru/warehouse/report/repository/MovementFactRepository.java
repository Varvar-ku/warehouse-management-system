package ru.warehouse.report.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.warehouse.common.event.StockMovementEvent;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MovementFactRepository {

    /** Условия отбора. to: правая граница НЕ включается. */
    public record Filter(Instant from, Instant to, UUID warehouseId, UUID productId, String sku,
                         String batchNumber, String documentNumber, UUID performedBy) {
    }

    public record Slice<T>(List<T> items, long total) {
    }

    private final JdbcClient jdbc;

    public MovementFactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Возвращает число вставленных строк: 0 значит, что событие уже было (повторная доставка). */
    public int insert(StockMovementEvent e, String contextJson) {
        return jdbc.sql("""
                        INSERT INTO movement_facts (
                            id, movement_type, warehouse_id, product_id, product_sku, product_name, unit_code,
                            batch_id, batch_number, expiry_at,
                            from_section_id, from_section_code, to_section_id, to_section_code,
                            quantity, document_type, document_id, document_number,
                            performed_by, performed_at, comment, trace_id, context)
                        VALUES (
                            :id, :type, :warehouseId, :productId, :sku, :name, :unit,
                            :batchId, :batchNumber, :expiryAt,
                            :fromSectionId, :fromSectionCode, :toSectionId, :toSectionCode,
                            :quantity, :documentType, :documentId, :documentNumber,
                            :performedBy, :performedAt, :comment, :traceId, CAST(:context AS jsonb))
                        ON CONFLICT (id) DO NOTHING
                        """)
                .param("id", e.movementId())
                .param("type", e.movementType())
                .param("warehouseId", e.warehouseId())
                .param("productId", e.productId())
                .param("sku", e.productSku())
                .param("name", e.productName())
                .param("unit", e.unitCode())
                .param("batchId", e.batchId())
                .param("batchNumber", e.batchNumber())
                .param("expiryAt", utc(e.expiryAt()))
                .param("fromSectionId", e.fromSectionId())
                .param("fromSectionCode", e.fromSectionCode())
                .param("toSectionId", e.toSectionId())
                .param("toSectionCode", e.toSectionCode())
                .param("quantity", e.quantity())
                .param("documentType", e.documentType())
                .param("documentId", e.documentId())
                .param("documentNumber", e.documentNumber())
                .param("performedBy", e.performedBy())
                .param("performedAt", utc(e.performedAt()))
                .param("comment", e.comment())
                .param("traceId", e.traceId())
                .param("context", contextJson)
                .update();
    }

    /**
     * Страница строк отчёта. fixedType: тип движения, зашитый в отчёт (null: берётся из typeFilter).
     * columns: готовый список колонок SELECT для конкретного отчёта (константа в сервисе, не ввод пользователя).
     */
    public <T> Slice<T> find(String fixedType, String typeFilter, Filter f, String columns,
                             RowMapper<T> mapper, int page, int size) {
        Map<String, Object> p = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        String type = fixedType != null ? fixedType : typeFilter;
        if (type != null) {
            where.append(" AND movement_type = :type");
            p.put("type", type);
        }
        if (f.from() != null) {
            where.append(" AND performed_at >= :from");
            p.put("from", utc(f.from()));
        }
        if (f.to() != null) {
            where.append(" AND performed_at < :to");
            p.put("to", utc(f.to()));
        }
        if (f.warehouseId() != null) {
            where.append(" AND warehouse_id = :warehouseId");
            p.put("warehouseId", f.warehouseId());
        }
        if (f.productId() != null) {
            where.append(" AND product_id = :productId");
            p.put("productId", f.productId());
        }
        if (f.sku() != null && !f.sku().isBlank()) {
            where.append(" AND lower(product_sku) LIKE lower(:sku)");
            p.put("sku", "%" + f.sku().trim() + "%");
        }
        if (f.batchNumber() != null && !f.batchNumber().isBlank()) {
            where.append(" AND batch_number = :batchNumber");
            p.put("batchNumber", f.batchNumber().trim());
        }
        if (f.documentNumber() != null && !f.documentNumber().isBlank()) {
            where.append(" AND document_number = :documentNumber");
            p.put("documentNumber", f.documentNumber().trim());
        }
        if (f.performedBy() != null) {
            where.append(" AND performed_by = :performedBy");
            p.put("performedBy", f.performedBy());
        }

        long total = jdbc.sql("SELECT count(*) FROM movement_facts" + where)
                .params(p).query(Long.class).single();

        Map<String, Object> pageParams = new HashMap<>(p);
        pageParams.put("limit", size);
        pageParams.put("offset", (long) page * size);
        List<T> items = jdbc.sql("SELECT " + columns + " FROM movement_facts" + where
                        + " ORDER BY performed_at DESC, id LIMIT :limit OFFSET :offset")
                .params(pageParams).query(mapper).list();
        return new Slice<>(items, total);
    }

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}

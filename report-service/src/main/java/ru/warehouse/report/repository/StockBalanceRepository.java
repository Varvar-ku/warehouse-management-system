package ru.warehouse.report.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Остатки, восстановленные из фактов движений: каждое движение прибавляет количество в секцию-получатель
 * (to_section) и вычитает из секции-источника (from_section). Одно правило покрывает поступление,
 * выдачу, списание, перемещение и корректировку (у корректировки заполнена одна из секций).
 */
@Repository
public class StockBalanceRepository {

    /** Условия отбора. asOfExclusive: правая граница НЕ включается (null: без ограничения по времени). */
    public record Filter(Instant asOfExclusive, UUID warehouseId, UUID sectionId, UUID productId,
                         String sku, String batchNumber, boolean includeZero) {
    }

    public record Slice<T>(List<T> items, long total) {
    }

    // Константа: пользовательский ввод в SQL не попадает. RESERVATION физический остаток не меняет.
    private static final String DELTAS = """
            SELECT warehouse_id, product_id, product_sku, product_name, unit_code, batch_id, batch_number, expiry_at,
                   to_section_id AS section_id, to_section_code AS section_code, quantity AS delta, performed_at
            FROM movement_facts
            WHERE to_section_id IS NOT NULL AND movement_type <> 'RESERVATION'
            UNION ALL
            SELECT warehouse_id, product_id, product_sku, product_name, unit_code, batch_id, batch_number, expiry_at,
                   from_section_id, from_section_code, -quantity, performed_at
            FROM movement_facts
            WHERE from_section_id IS NOT NULL AND movement_type <> 'RESERVATION'
            """;

    private static final String GROUP_BY =
            " GROUP BY warehouse_id, section_id, product_id, batch_id, unit_code";

    private final JdbcClient jdbc;

    public StockBalanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public <T> Slice<T> find(Filter f, RowMapper<T> mapper, int page, int size) {
        Map<String, Object> p = new HashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (f.asOfExclusive() != null) {
            where.append(" AND performed_at < :asOf");
            p.put("asOf", utc(f.asOfExclusive()));
        }
        if (f.warehouseId() != null) {
            where.append(" AND warehouse_id = :warehouseId");
            p.put("warehouseId", f.warehouseId());
        }
        if (f.sectionId() != null) {
            where.append(" AND section_id = :sectionId");
            p.put("sectionId", f.sectionId());
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
        // Нулевые строки скрываем, отрицательные оставляем: это признак пропущенного события
        String having = f.includeZero() ? "" : " HAVING sum(delta) <> 0";
        String grouped = " FROM (" + DELTAS + ") d" + where + GROUP_BY + having;

        long total = jdbc.sql("SELECT count(*) FROM (SELECT 1" + grouped + ") t")
                .params(p).query(Long.class).single();

        Map<String, Object> pageParams = new HashMap<>(p);
        pageParams.put("limit", size);
        pageParams.put("offset", (long) page * size);
        List<T> items = jdbc.sql("""
                        SELECT warehouse_id, section_id, max(section_code) AS section_code,
                               product_id, max(product_sku) AS product_sku, max(product_name) AS product_name,
                               batch_id, max(batch_number) AS batch_number, max(expiry_at) AS expiry_at,
                               unit_code, sum(delta) AS quantity""" + grouped
                        + " ORDER BY section_code, product_sku, batch_number, section_id, batch_id"
                        + " LIMIT :limit OFFSET :offset")
                .params(pageParams).query(mapper).list();
        return new Slice<>(items, total);
    }

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }
}

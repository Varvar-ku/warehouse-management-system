package ru.warehouse.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * S1: поиск существующей партии и строки остатка. Партия определяется парой (номенклатура, номер партии):
 * повторная поставка той же партии пополняет её, а не отвергается. Запросы идут через JDBC, чтобы не зависеть от
 * состава репозиториев и не ловить незавершённый flush Hibernate.
 */
@Component
@RequiredArgsConstructor
public class BatchLookup {
    public record BatchRef(UUID id, String unitCode, OffsetDateTime expiryAt) {}

    private final JdbcClient jdbc;

    public Optional<BatchRef> find(UUID productId, String batchNumber) {
        return jdbc.sql("select id, unit_code, expiry_at from batches where product_id = :p and batch_number = :n")
                .param("p", productId).param("n", batchNumber)
                .query((rs, i) -> new BatchRef(rs.getObject("id", UUID.class), rs.getString("unit_code"),
                        rs.getObject("expiry_at", OffsetDateTime.class)))
                .optional();
    }

    public Optional<UUID> findStockId(UUID productId, UUID sectionId, UUID batchId) {
        return jdbc.sql("select id from stock where product_id = :p and section_id = :s and batch_id = :b")
                .param("p", productId).param("s", sectionId).param("b", batchId)
                .query(UUID.class).optional();
    }

    /** null, если позицию можно добавить в существующую партию; иначе описание конфликта. */
    public static String mismatch(BatchRef batch, String unitCode, OffsetDateTime expiryAt) {
        if (!batch.unitCode().equalsIgnoreCase(unitCode)) {
            return "партия уже существует в другой единице измерения (" + batch.unitCode() + ")";
        }
        if (!sameInstant(batch.expiryAt(), expiryAt)) {
            return "партия уже существует с другим сроком годности (" + batch.expiryAt() + "): срок партии не меняется";
        }
        return null;
    }

    private static boolean sameInstant(OffsetDateTime a, OffsetDateTime b) {
        if (a == null || b == null) return a == b;
        return a.toInstant().truncatedTo(ChronoUnit.MICROS).equals(b.toInstant().truncatedTo(ChronoUnit.MICROS));
    }
}

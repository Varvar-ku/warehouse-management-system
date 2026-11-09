package ru.warehouse.inventory.it;

import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Данные для интеграционных тестов inventory: приёмка, партия и строка остатка напрямую в БД (минуя сервисы). */
final class StockFixtures {
    private StockFixtures() {}

    static final OffsetDateTime NO_EXPIRY = OffsetDateTime.now().plusYears(20);

    record Spec(String section, String batchNumber, OffsetDateTime expiry, String quantity,
                boolean unassignedZone, boolean sectionBlocked, boolean onHold) {
        static Spec plain(String section, String batch, String quantity) {
            return new Spec(section, batch, NO_EXPIRY, quantity, false, false, false);
        }
        Spec expiring(OffsetDateTime at) { return new Spec(section, batchNumber, at, quantity, unassignedZone, sectionBlocked, onHold); }
        Spec unassigned() { return new Spec(section, batchNumber, expiry, quantity, true, sectionBlocked, onHold); }
        Spec blocked() { return new Spec(section, batchNumber, expiry, quantity, unassignedZone, true, onHold); }
        Spec held() { return new Spec(section, batchNumber, expiry, quantity, unassignedZone, sectionBlocked, true); }
    }

    /** Возвращает идентификатор строки остатка. */
    static UUID stock(JdbcTemplate jdbc, UUID warehouse, UUID product, Spec spec) {
        UUID receipt = UUID.randomUUID();
        jdbc.update("INSERT INTO receipts (id, number, warehouse_id, supplier_name, received_at, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                receipt, "T-" + receipt, warehouse, "supplier", OffsetDateTime.now(), UUID.randomUUID());
        UUID batch = UUID.randomUUID();
        jdbc.update("INSERT INTO batches (id, product_id, product_sku, product_name, unit_code, receipt_id, "
                        + "batch_number, received_at, expiry_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batch, product, "SKU-" + product, "Товар", "PCS", receipt, spec.batchNumber() + "-" + batch,
                OffsetDateTime.now(), spec.expiry());
        UUID stock = UUID.randomUUID();
        jdbc.update("INSERT INTO stock (id, warehouse_id, product_id, section_id, section_code, batch_id, unit_code, "
                        + "quantity, unassigned_zone, section_blocked, on_hold) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                stock, warehouse, product, UUID.randomUUID(), spec.section(), batch, "PCS",
                new BigDecimal(spec.quantity()), spec.unassignedZone(), spec.sectionBlocked(), spec.onHold());
        return stock;
    }
}

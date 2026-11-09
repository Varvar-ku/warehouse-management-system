package ru.warehouse.inventory.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * M4: чем заняты номенклатура и склад в inventory-service. Нужен catalog-service (архивация товара) и
 * warehouse-service (отключение склада): нельзя убирать то, на что ссылаются остатки и открытые документы.
 */
@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STOREKEEPER', 'ADMIN')")
public class UsageController {
    private static final String OPEN_RECEIPTS = "('DRAFT', 'RECEIVING', 'PLACEMENT')";

    public record ProductUsage(BigDecimal stockQuantity, long openReceipts, long activeReservations) {}

    public record WarehouseUsage(BigDecimal stockQuantity, long openReceipts, long draftWriteOffs,
                                 long draftIssues, long draftAdjustments) {}

    private final JdbcClient jdbc;

    @GetMapping("/products/{productId}/usage")
    public ProductUsage product(@PathVariable UUID productId) {
        BigDecimal stock = jdbc.sql("SELECT COALESCE(SUM(quantity), 0) FROM stock WHERE product_id = :id AND quantity > 0")
                .param("id", productId).query(BigDecimal.class).single();
        long receipts = count("""
                SELECT count(DISTINCT r.id) FROM receipts r JOIN receipt_items i ON i.receipt_id = r.id
                WHERE i.product_id = :id AND r.status IN %s""".formatted(OPEN_RECEIPTS), productId);
        long reservations = count("""
                SELECT count(*) FROM reservations res JOIN stock s ON s.id = res.stock_id
                WHERE s.product_id = :id AND res.status = 'ACTIVE'""", productId);
        return new ProductUsage(stock, receipts, reservations);
    }

    @GetMapping("/warehouses/{warehouseId}/usage")
    public WarehouseUsage warehouse(@PathVariable UUID warehouseId) {
        BigDecimal stock = jdbc.sql("SELECT COALESCE(SUM(quantity), 0) FROM stock WHERE warehouse_id = :id AND quantity > 0")
                .param("id", warehouseId).query(BigDecimal.class).single();
        return new WarehouseUsage(stock,
                count("SELECT count(*) FROM receipts WHERE warehouse_id = :id AND status IN " + OPEN_RECEIPTS, warehouseId),
                count("SELECT count(*) FROM write_offs WHERE warehouse_id = :id AND status = 'DRAFT'", warehouseId),
                count("SELECT count(*) FROM issues WHERE warehouse_id = :id AND status = 'DRAFT'", warehouseId),
                count("SELECT count(*) FROM stock_adjustments WHERE warehouse_id = :id AND status = 'DRAFT'", warehouseId));
    }

    private long count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }
}

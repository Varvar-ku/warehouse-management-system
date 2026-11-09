package ru.warehouse.report.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Общие параметры отчётов (query string). from/to: даты yyyy-MM-dd, обе границы включительно,
 * сутки считаются в часовом поясе app.report.zone.
 */
public record ReportQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        UUID warehouseId,
        UUID productId,
        String sku,
        String batchNumber,
        String documentNumber,
        UUID performedBy,
        String type,
        Integer page,
        Integer size) {
}

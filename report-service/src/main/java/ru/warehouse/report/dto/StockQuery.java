package ru.warehouse.report.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Параметры отчёта «Остатки». asOf: дата yyyy-MM-dd, остаток на конец этих суток (в app.report.zone);
 * без asOf берётся всё накопленное на текущий момент. includeZero: показывать нулевые строки.
 */
public record StockQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
        UUID warehouseId,
        UUID sectionId,
        UUID productId,
        String sku,
        String batchNumber,
        Boolean includeZero,
        Integer page,
        Integer size) {
}

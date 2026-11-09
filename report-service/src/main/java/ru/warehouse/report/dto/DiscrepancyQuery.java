package ru.warehouse.report.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Параметры отчёта «Расхождения». source: REQUEST или RECEIPT. unresolvedOnly: только нерешённые
 * расхождения приёмки (для строк заявок понятие решения не применяется).
 */
public record DiscrepancyQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        String source,
        String documentNumber,
        String sku,
        UUID responsibleId,
        Boolean unresolvedOnly,
        Integer page,
        Integer size) {
}

package ru.warehouse.report.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/** Параметры отчётов по комплектации. from/to: даты yyyy-MM-dd, обе границы включительно. */
public record PickingQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        UUID workerId,
        String taskNumber,
        String action,
        Integer page,
        Integer size) {
}

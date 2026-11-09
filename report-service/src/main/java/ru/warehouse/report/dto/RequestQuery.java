package ru.warehouse.report.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/** Параметры отчёта «Заявки». Период from/to (yyyy-MM-dd, обе границы включительно) отбирает по дате создания. */
public record RequestQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        String status,
        UUID requesterId,
        String number,
        UUID warehouseId,
        Integer page,
        Integer size) {
}
